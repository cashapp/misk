package misk.client

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.squareup.wire.GrpcMethod
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.metrics.v2.Metrics
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ClientMetricsInterceptor private constructor(
  val clientName: String,
  private val requestDurationSummary: Summary,
  private val requestDurationHistogram: Histogram,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val actionName = actionName(chain)
      ?: return chain.proceed(chain.request())

    val stopwatch = Stopwatch.createStarted(Ticker.systemTicker())
    try {
      val result = chain.proceed(chain.request())
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDurationSummary.labels(actionName, "${result.code}").observe(elapsedMillis)
      requestDurationHistogram.labels(actionName, "${result.code}").observe(elapsedMillis)
      return result
    } catch (e: SocketTimeoutException) {
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDurationSummary.labels(actionName, "timeout").observe(elapsedMillis)
      requestDurationHistogram.labels(actionName, "timeout").observe(elapsedMillis)
      throw e
    } catch (e: Exception) {
      // Something else happened while the connection was in progress and we didn't receive
      // a complete response. We still want to record any long-running calls, however.
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDurationSummary.labels(actionName, "incomplete-response").observe(elapsedMillis)
      requestDurationHistogram.labels(actionName, "incomplete-response").observe(elapsedMillis)
      throw e
    }
  }

  private fun actionName(chain: Interceptor.Chain): String? {
    val invocation = chain.request().tag(Invocation::class.java)
    if (invocation != null) return "$clientName.${invocation.method().name}"

    val grpcMethod = chain.request().tag(GrpcMethod::class.java)
    if (grpcMethod != null) return "$clientName.${grpcMethod.path.substringAfterLast("/")}"

    val url = chain.request().tag(URL::class.java)
    if (url != null) return "$clientName.${url.path.trim('/').replace('/', '.')}"

    return null
  }

  @Singleton
  class Factory @Inject internal constructor(
    m: Metrics,
    config: PrometheusConfig,
  ) {
    internal val requestDuration = m.summary(
      name = "client_http_request_latency_ms",
      help = "count and duration in ms of outgoing client requests",
      labelNames = listOf("action", "code"),
      maxAgeSeconds = config.max_age_in_seconds,
    )
    internal val requestDurationHistogram = m.histogram(
      name = "histo_client_http_request_latency_ms",
      help = "histogram in ms of outgoing client requests",
      labelNames = listOf("action", "code")
    )

    fun create(clientName: String) = ClientMetricsInterceptor(clientName, requestDuration, requestDurationHistogram)
  }
}
