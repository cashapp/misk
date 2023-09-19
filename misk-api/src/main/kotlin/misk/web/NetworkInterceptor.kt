package misk.web

import misk.Action

interface NetworkInterceptor {
  fun intercept(chain: NetworkChain)

  interface Factory {
    fun create(action: Action): NetworkInterceptor?
  }
}
