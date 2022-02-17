package misk.web.interceptors

import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import misk.Action
import misk.MiskCaller
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.metrics.v2.Metrics
import misk.scope.ActionScoped
import misk.time.timed
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import javax.inject.Inject
import javax.inject.Singleton

internal class MetricsInterceptor internal constructor(
  private val actionName: String,
  private val requestDurationSummary: Summary,
  private val requestDurationHistogram: Histogram,
  private val caller: ActionScoped<MiskCaller?>
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val (elapsedTime, result) = timed { chain.proceed(chain.httpCall) }

    val elapsedTimeMillis = elapsedTime.toMillis().toDouble()
    val callingPrincipal = when {
      caller.get()?.service != null -> caller.get()?.service!!
      caller.get()?.user != null -> "<user>"
      else -> "unknown"
    }

    val statusCode = chain.httpCall.statusCode
    requestDurationSummary
      .labels(actionName, callingPrincipal, statusCode.toString())
      .observe(elapsedTimeMillis)
    requestDurationHistogram
      .labels(actionName, callingPrincipal, statusCode.toString())
      .observe(elapsedTimeMillis)
    return result
  }

  @Singleton
  class Factory @Inject constructor(
    m: Metrics,
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    config: PrometheusConfig,
  ) : NetworkInterceptor.Factory {
    internal val requestDuration = m.summary(
      name = "http_request_latency_ms",
      help = "count and duration in ms of incoming web requests",
      labelNames = listOf("action", "caller", "code"),
      maxAgeSeconds = config.max_age_in_seconds,
    )
    private val requestDurationHistogram = m.histogram(
      name = "histo_http_request_latency_ms",
      help = "count and duration in ms of incoming web requests",
      labelNames = listOf("action", "caller", "code")
    )

    override fun create(action: Action) = MetricsInterceptor(
      action.name,
      requestDuration,
      requestDurationHistogram,
      caller,
    )
  }
}
