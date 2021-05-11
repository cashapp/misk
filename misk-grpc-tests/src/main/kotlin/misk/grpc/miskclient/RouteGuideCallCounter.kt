package misk.grpc.miskclient

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import misk.client.ClientAction
import misk.client.ClientNetworkChain
import misk.client.ClientNetworkInterceptor
import okhttp3.Response

/** Sample network interceptor to confirm they're executed for outbound gRPC calls. */
@Singleton
class RouteGuideCallCounter @Inject constructor() : ClientNetworkInterceptor.Factory {
  val actionNameToCount = Collections.synchronizedMap(mutableMapOf<String, AtomicInteger>())

  fun counter(name: String): AtomicInteger {
    return actionNameToCount.getOrPut(name) { AtomicInteger() }
  }

  override fun create(action: ClientAction): ClientNetworkInterceptor? {
    val counter = counter(action.name)
    return ActionCallCounter(counter)
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
