package misk.client

import retrofit2.Call

/**
 * Intercepts client side calls at the application level, able to view and modify the
 * outgoing request arguments, and to observe the application level response
 */
interface ClientApplicationInterceptor {
  /** Intercepts the start of a new call. Can view or modify the arguments to the call */
  fun interceptBeginCall(chain: BeginClientCallChain): Call<Any>

  /**
   * Intercepts the execution of a call. Can observe / modify the outgoing request, and
   * can register a callback to observe the response
   */
  fun intercept(chain: ClientChain)

  /**
   * This interface is used with Guice multibindings. Register instances by calling `multibind()`
   * in a `KAbstractModule`:
   *
   * ```
   * multibind<ClientApplicationInterceptor.Factory>().to<MyFactory>()
   * ```
   */
  interface Factory {
    fun create(action: ClientAction): ClientApplicationInterceptor?
  }
}
