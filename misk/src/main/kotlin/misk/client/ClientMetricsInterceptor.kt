package misk.client

import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import misk.metrics.Histogram
import misk.metrics.Metrics
import okhttp3.Response
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ClientMetricsInterceptor internal constructor(
  private val actionName: String,
  private val requestDuration: Histogram
) : ClientNetworkInterceptor {

  override fun intercept(chain: ClientNetworkChain): Response {
    val stopwatch = Stopwatch.createStarted(Ticker.systemTicker())
    try {
      val result = chain.proceed(chain.request)
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDuration.record(elapsedMillis, actionName, "${result.code}")
      return result
    } catch (e: SocketTimeoutException) {
      val elapsedMillis = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS).toDouble()
      requestDuration.record(elapsedMillis, actionName, "timeout")
      throw e
    }
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
