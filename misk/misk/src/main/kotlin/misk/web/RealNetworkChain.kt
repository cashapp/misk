package misk.web

import misk.Action
import misk.web.actions.WebAction

internal class RealNetworkChain(
  override val action: Action,
  override val webAction: WebAction,
  override val request: Request,
  private val interceptors: List<NetworkInterceptor>,
  private val index: Int = 0
) : NetworkChain {
  override fun proceed(request: Request) {
    check(index < interceptors.size) { "final interceptor must be terminal" }
    val next = RealNetworkChain(action, webAction, request, interceptors, index + 1)
    return interceptors[index].intercept(next)
  }
}
