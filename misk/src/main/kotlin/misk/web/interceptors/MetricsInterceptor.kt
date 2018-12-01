package misk.web.interceptors

import misk.Action
import misk.MiskCaller
import misk.metrics.Histogram
import misk.metrics.Metrics
import misk.scope.ActionScoped
import misk.time.timed
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import javax.inject.Inject
import javax.inject.Singleton

internal class MetricsInterceptor internal constructor(
  private val actionName: String,
  private val requestDuration: Histogram,
  private val caller: ActionScoped<MiskCaller?>
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {
    val (elapsedTime, result) = timed { chain.proceed(chain.request) }

    val elapsedTimeMillis = elapsedTime.toMillis().toDouble()
    val callingPrincipal = caller.get()?.principal ?: "unknown"

    arrayOf("all", "${result.statusCode / 100}xx", "${result.statusCode}").forEach { code ->
      requestDuration.record(elapsedTimeMillis, actionName, callingPrincipal, code)
    }
    return result
  }

  @Singleton
  class Factory @Inject constructor(
    m: Metrics,
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>
  ) : NetworkInterceptor.Factory {
    internal val requestDuration = m.histogram(
        name = "http_request_latency_ms",
        help = "count and duration in ms of incoming web requests",
        labelNames = listOf("action", "caller", "code"))

    override fun create(action: Action) = MetricsInterceptor(action.name, requestDuration, caller)
  }
}
