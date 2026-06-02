package misk.client

import okhttp3.Interceptor

class NetworkInterceptorWrapper(val action: ClientAction, val interceptor: ClientNetworkInterceptor) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
    return interceptor.intercept(RealClientNetworkChain(chain, action))
  }
}
