package misk.client

import okhttp3.Call

/**
 *
 * CallFactoryWrapper is a way to extend the functionality of the [okhttp3.Call.Factory] instance
 * injected to typed http clients and gRPC clients created by
 * [TypedClientFactoryProvider] and [GrpcClientProvider].
 *
 * The ability to extend the [Call.Factory] instance gives you similar functionalities to
 * [ClientApplicationInterceptorFactory] and [ClientNetworkInterceptor], where you can modify
 * the request/response of the outgoing request. The main difference is that Call.Factory will be
 * run in the thread the client is making the request as opposite to
 * [ClientApplicationInterceptorFactory] and [ClientNetworkInterceptor], which might be run on
 * the [okhttp3.Dispatcher] threads when executing requests asynchronously. That can be problematic
 * if [ActionScoped] or [ThreadLocal] information wants to be propagated using interceptors
 * to downstream services. For example, tracing related information.
 *
 * This interface is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 * ```
 * multibind<ClientNetworkInterceptor.Factory>().to<MyFactory>()
 * ```
 *
 */
interface CallFactoryWrapper {

  /**
   * Returns a [Call.Factory] that wraps the original call factory `delegate` pass as argument.
   */
  fun wrap(action: ClientAction, delegate: Call.Factory): Call.Factory?
}
