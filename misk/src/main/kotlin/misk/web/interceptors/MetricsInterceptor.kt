package misk.web.interceptors

import misk.Action
import misk.Chain
import misk.Interceptor
import misk.metrics.Metrics
import misk.metrics.MetricsScope
import misk.web.Response
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

internal class MetricsInterceptor internal constructor(val scope: MetricsScope) : Interceptor {

  @Singleton
  class Factory @Inject constructor(val m: Metrics) : Interceptor.Factory {
    override fun create(action: Action): Interceptor? =
        MetricsInterceptor(m.scope("web.${action.name}"))
  }

  override fun intercept(chain: Chain): Any? {
    scope.counter("requests")
        .inc()
    val result = scope.timer("timing")
        .time(Callable { chain.proceed(chain.args) })
    if (result is Response<*>) {
      scope.counter("responses.${result.statusCode / 100}xx")
          .inc()
      scope.counter("responses.${result.statusCode}")
          .inc()
    }

    return result
  }
}
