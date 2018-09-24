package misk.client

import misk.metrics.Histogram
import misk.metrics.Metrics
import misk.time.timed
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

class ClientMetricsInterceptor internal constructor(
  private val actionName: String,
  private val requestDuration: Histogram
) : ClientNetworkInterceptor {

  override fun intercept(chain: ClientNetworkChain): Response {
    val (elapsedTime, result) = timed { chain.proceed(chain.request) }
    val elapsedMillis = elapsedTime.toMillis().toDouble()

    requestDuration.record(elapsedMillis, actionName, "all")
    requestDuration.record(elapsedMillis, actionName, "${result.code() / 100}xx")
    requestDuration.record(elapsedMillis, actionName, "${result.code()}")

    return result
  }

  @Singleton
  class Factory @Inject internal constructor(m: Metrics) : ClientNetworkInterceptor.Factory {
    internal val requestDuration = m.histogram(
        name = "client_http_request_latency_ms",
        help = "count and duration in ms of outgoing client requests",
        labelNames = listOf("action", "code"))

    override fun create(action: ClientAction) =
        ClientMetricsInterceptor(action.name, requestDuration)
  }
}