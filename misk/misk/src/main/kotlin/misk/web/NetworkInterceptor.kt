package misk.web

import misk.Action

interface NetworkInterceptor {
  fun intercept(chain: NetworkChain): Response<*>

  interface Factory {
    fun create(action: Action): NetworkInterceptor?
  }
}
