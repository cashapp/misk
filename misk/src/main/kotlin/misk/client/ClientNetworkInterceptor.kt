package misk.client

import okhttp3.Response

/**
 * Intercepts client side calls at the application level, able to view and modify the
 * outgoing HTTP request and observe the returned HTTP response
 */
interface ClientNetworkInterceptor {
  fun intercept(chain: ClientNetworkChain): Response

  /**
   * This interface is used with Guice multibindings. Register instances by calling `multibind()`
   * in a `KAbstractModule`:
   *
   * ```
   * multibind<ClientNetworkInterceptor.Factory>().to<MyFactory>()
   * ```
   */
  interface Factory {
    fun create(action: ClientAction): ClientNetworkInterceptor?
  }
}
