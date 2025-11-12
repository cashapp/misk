package misk.web.interceptors

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.Meter
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.MiskCaller
import misk.metrics.backends.prometheus.PrometheusConfig
import misk.metrics.v2.Metrics
import misk.scope.ActionScoped
import misk.time.timed
import misk.web.NetworkChain
import misk.web.NetworkInterceptor

internal class MetricsInterceptor internal constructor(
  private val actionName: String,
  private val requestDurationSummary: Summary?,
  private val requestDurationHistogram: Histogram,
  private val otelRequestDuration: DoubleHistogram,
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
      ?.labels(actionName, callingPrincipal, statusCode.toString())
      ?.observe(elapsedTimeMillis)
    requestDurationHistogram
      .labels(actionName, callingPrincipal, statusCode.toString())
      .observe(elapsedTimeMillis)

    otelRequestDuration.record(
      elapsedTime.toNanos() / 1_000_000_000.0,
      Attributes.of(
        AttributeKey.stringKey("http.route"), actionName,
        AttributeKey.stringKey("http.response.status_code"), statusCode.toString(),
        AttributeKey.stringKey("client.service.name"), callingPrincipal
      )
    )

    return result
  }

  @Singleton
  class Factory @Inject constructor(
    m: Metrics,
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    config: PrometheusConfig,
    private val meter: Meter,
  ) : NetworkInterceptor.Factory {
    internal val requestDurationSummary = when (config.disable_default_summary_metrics) {
      true -> null
      false -> m.summary(
        name = "http_request_latency_ms",
        help = "count and duration in ms of incoming web requests",
        labelNames = listOf("action", "caller", "code"),
        maxAgeSeconds = config.max_age_in_seconds,
      )
    }

    internal val requestDurationHistogram = m.histogram(
      name = "histo_http_request_latency_ms",
      help = "count and duration in ms of incoming web requests",
      labelNames = listOf("action", "caller", "code")
    )

    internal val otelRequestDuration = meter
      .histogramBuilder("http.server.request.duration")
      .setDescription("Duration of HTTP server requests")
      .setUnit("s")
      .setExplicitBucketBoundariesAdvice(listOf(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0))
      .build()

    override fun create(action: Action) = MetricsInterceptor(
      action.name,
      requestDurationSummary,
      requestDurationHistogram,
      otelRequestDuration,
      caller,
    )
  }
}
