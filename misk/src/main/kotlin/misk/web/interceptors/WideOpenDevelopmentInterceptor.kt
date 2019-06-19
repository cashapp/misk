package misk.web.interceptors

import misk.Action
import misk.environment.Environment
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import javax.inject.Inject

internal class WideOpenDevelopmentInterceptor @Inject constructor() : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    chain.httpCall.setResponseHeader("Access-Control-Allow-Origin", "*")
    chain.proceed(chain.httpCall)
  }
}

internal class WideOpenDevelopmentInterceptorFactory @Inject constructor(
  private val wideOpenDevelopmentInterceptor: WideOpenDevelopmentInterceptor,
  private val environment: Environment
) : NetworkInterceptor.Factory {

  override fun create(action: Action): NetworkInterceptor? {
    if (environment == Environment.DEVELOPMENT) {
      return wideOpenDevelopmentInterceptor
    }
    return null
  }
}
