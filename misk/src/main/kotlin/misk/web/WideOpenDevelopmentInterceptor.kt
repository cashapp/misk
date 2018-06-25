package misk.web

import misk.Action
import misk.environment.Environment
import javax.inject.Inject

internal class WideOpenDevelopmentInterceptor : NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {
    val response = chain.proceed(chain.request)
    return response.copy(
        headers = response.headers.newBuilder()
            .add("Access-Control-Allow-Origin", "*")
            .build()
    )
  }
}

internal class WideOpenDevelopmentInterceptorFactory : NetworkInterceptor.Factory {
  @Inject lateinit var wideOpenDevelopmentInterceptor: WideOpenDevelopmentInterceptor
  @Inject lateinit var environment: Environment

  override fun create(action: Action): NetworkInterceptor? {
    if (environment == Environment.DEVELOPMENT) {
      return wideOpenDevelopmentInterceptor
    }
    return null
  }
}

