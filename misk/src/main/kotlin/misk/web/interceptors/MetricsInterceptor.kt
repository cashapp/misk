package misk.web.interceptors

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.metrics.Metrics
import misk.metrics.MetricsScope
import misk.web.Response
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

internal class MetricsInterceptor internal constructor(val scope: MetricsScope) :
    NetworkInterceptor {

  @Singleton
  class Factory @Inject constructor(val m: Metrics) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? =
        MetricsInterceptor(m.scope("web.${action.name}"))
  }

  override fun intercept(chain: NetworkChain): Response<*> {
    scope.counter("requests").inc()
    val result = scope.timer("timing").time(Callable { chain.proceed(chain.request) })
    if (result != null) {
      scope.counter("responses.${result.statusCode / 100}xx").inc()
      scope.counter("responses.${result.statusCode}").inc()
    }

    return result
  }
}
