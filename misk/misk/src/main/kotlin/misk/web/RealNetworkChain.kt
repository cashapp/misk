package misk.web

import misk.web.actions.WebAction
import kotlin.reflect.KFunction

internal class RealNetworkChain(
  override val action: WebAction,
  override val request: Request,
  private val interceptors: List<NetworkInterceptor>,
  override val function: KFunction<*>,
  private val index: Int = 0
) : NetworkChain {
  override fun proceed(request: Request): Response<*> {
    check(index < interceptors.size) { "final interceptor must be terminal" }
    // NB(young): At this point it's possible the Request has been manipulated by Network
    // Interceptors to the point where it now matches another WebAction. Re-binding and starting
    // a new Network chain is TECHNICALLY an option, but is probably a crazy thing to do.
    // If this is something we want to enable, it should be in another layer of interception prior
    // to Action binding.
    val next = RealNetworkChain(action, request, interceptors, function, index + 1)
    return interceptors[index].intercept(next)
  }
}
