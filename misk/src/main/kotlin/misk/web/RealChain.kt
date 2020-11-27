package misk.web

import misk.Chain
import misk.ApplicationInterceptor
import misk.web.actions.WebAction
import kotlin.reflect.KFunction

internal class RealChain(
  override val action: WebAction,
  override val args: List<Any?>,
  private val interceptors: List<ApplicationInterceptor>,
  override val function: KFunction<*>,
  override val httpCall: HttpCall,
  private val index: Int = 0
) : Chain {
  override fun proceed(args: List<Any?>): Any {
    check(index < interceptors.size) { "final interceptor must be terminal" }
    val next = RealChain(action, args, interceptors, function, httpCall, index + 1)
    return interceptors[index].intercept(next)
  }
}
