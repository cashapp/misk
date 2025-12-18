package misk.web.interceptors

import jakarta.inject.Inject
import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import wisp.deployment.Deployment

class WideOpenDevelopmentInterceptor @Inject constructor() : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    chain.httpCall.setResponseHeader("Access-Control-Allow-Origin", "*")
    chain.proceed(chain.httpCall)
  }
}

class WideOpenDevelopmentInterceptorFactory
@Inject
constructor(
  private val wideOpenDevelopmentInterceptor: WideOpenDevelopmentInterceptor,
  private val deployment: Deployment,
) : NetworkInterceptor.Factory {

  override fun create(action: Action): NetworkInterceptor? {
    if (deployment.isLocalDevelopment) {
      return wideOpenDevelopmentInterceptor
    }
    return null
  }
}
