package misk

import misk.web.Response

interface NetworkInterceptor {
  fun intercept(chain: NetworkChain): Response<*>

  interface Factory {
    fun create(action: Action): NetworkInterceptor?
  }
}
