package misk.web

import misk.Action
import misk.web.actions.WebAction

internal class RealNetworkChain(
  override val action: Action,
  override val webAction: WebAction,
  override val httpCall: HttpCall,
  private val interceptors: List<NetworkInterceptor>,
  private val index: Int = 0
) : NetworkChain {
  override fun proceed(httpCall: HttpCall) {
    check(index < interceptors.size) { "final interceptor must be terminal" }
    val next = RealNetworkChain(action, webAction, httpCall, interceptors, index + 1)
    return interceptors[index].intercept(next)
  }
}
