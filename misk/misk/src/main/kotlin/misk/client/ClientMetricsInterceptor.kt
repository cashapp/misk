package misk.client

import misk.metrics.Metrics
import misk.metrics.MetricsScope
import okhttp3.Response
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

class ClientMetricsInterceptor internal constructor(
  private val scope: MetricsScope
) : ClientNetworkInterceptor {
  override fun intercept(chain: ClientNetworkChain): Response {
    scope.counter("requests").inc()
    val result = scope.timer("timing").time(Callable { chain.proceed(chain.request) })
    if (result != null) {
      scope.counter("responses.${result.code() / 100}xx").inc()
      scope.counter("responses.${result.code()}").inc()
    }

    return result
  }

  @Singleton
  class Factory @Inject internal constructor(
    private val m: Metrics
  ) : ClientNetworkInterceptor.Factory {
    override fun create(action: ClientAction) =
        ClientMetricsInterceptor(m.scope("clients.http.${action.name}"))
  }
}