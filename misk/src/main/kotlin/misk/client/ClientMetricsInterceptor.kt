package misk.client

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.squareup.wire.GrpcMethod
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import misk.metrics.Histogram
import misk.metrics.Metrics
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

class ClientMetricsInterceptor private constructor(
  val clientName: String,
  private val requestDuration: Histogram
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val actionName = actionName(chain)
      ?: return chain.proceed(chain.request())

    val stopwatch = Stopwatch.createStarted(Ticker.systemTicker())
    try {
      val result = chain.proceed(chain.request())
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDuration.record(elapsedMillis, actionName, "${result.code}")
      return result
    } catch (e: SocketTimeoutException) {
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDuration.record(elapsedMillis, actionName, "timeout")
      throw e
    }
  }

  private fun actionName(chain: Interceptor.Chain): String? {
    val invocation = chain.request().tag(Invocation::class.java)
    if (invocation != null) return "$clientName.${invocation.method().name}"

    val grpcMethod = chain.request().tag(GrpcMethod::class.java)
    if (grpcMethod != null) return "$clientName.${grpcMethod.path.substringAfterLast("/")}"

    return null
  }

  @Singleton
  class Factory @Inject internal constructor(m: Metrics) {
    internal val requestDuration = m.histogram(
        name = "client_http_request_latency_ms",
        help = "count and duration in ms of outgoing client requests",
        labelNames = listOf("action", "code"))

    fun create(clientName: String) = ClientMetricsInterceptor(clientName, requestDuration)
  }
}
