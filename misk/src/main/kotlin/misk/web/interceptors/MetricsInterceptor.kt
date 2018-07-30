package misk.web.interceptors

import io.prometheus.client.Histogram
import misk.Action
import misk.metrics.Metrics
import misk.time.timed
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import javax.inject.Inject
import javax.inject.Singleton

internal class MetricsInterceptor internal constructor(
  private val actionName: String,
  private val requestDuration: Histogram
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {
    val (elapsedTime, result) = timed { chain.proceed(chain.request) }

    val elapsedTimeMillis = elapsedTime.toMillis().toDouble()
    requestDuration.labels(actionName, "all").observe(elapsedTimeMillis)
    requestDuration.labels(actionName, "${result.statusCode / 100}xx").observe(elapsedTimeMillis)
    requestDuration.labels(actionName, "${result.statusCode}").observe(elapsedTimeMillis)

    return result
  }

  @Singleton
  class Factory @Inject constructor(m: Metrics) : NetworkInterceptor.Factory {
    internal val requestDuration = m.histogram(
        name = "http_request_latency_ms",
        help = "count and duration in ms of incoming web requests",
        labelNames = listOf("action", "code"))

    override fun create(action: Action) = MetricsInterceptor(action.name, requestDuration)
  }
}
