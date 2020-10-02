package misk.grpc.miskclient

import misk.client.ClientAction
import misk.client.ClientNetworkChain
import misk.client.ClientNetworkInterceptor
import okhttp3.Response
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Sample network interceptor to confirm they're executed for outbound gRPC calls. */
@Singleton
class RouteGuideCallCounter @Inject constructor() : ClientNetworkInterceptor.Factory {
  val actionNameToCount = Collections.synchronizedMap(mutableMapOf<String, AtomicInteger>())

  override fun create(action: ClientAction): ClientNetworkInterceptor? {
    val count = actionNameToCount.getOrPut(action.name) { AtomicInteger() }
    return ActionCallCounter(count)
  }

  private class ActionCallCounter(
    private val counter: AtomicInteger
  ) : ClientNetworkInterceptor {
    override fun intercept(chain: ClientNetworkChain): Response {
      counter.incrementAndGet()
      return chain.proceed(chain.request)
    }
  }
}
