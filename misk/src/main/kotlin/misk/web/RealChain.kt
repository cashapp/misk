package misk.web

import misk.Chain
import misk.Interceptor
import misk.web.actions.WebAction
import kotlin.reflect.KFunction

internal class RealChain(
    private val _action: WebAction,
    private val _args: List<Any?>,
    private val interceptors: List<Interceptor>,
    private val _function: KFunction<*>,
    private val index: Int = 0
) : Chain {

  override val action: WebAction
    get() = _action

  override val args: List<Any?>
    get() = _args

  override val function: KFunction<*>
    get() = _function

  override fun proceed(args: List<Any?>): Any? {
    check(index < interceptors.size) { "final interceptor must be terminal" }
    val next = RealChain(_action, args, interceptors, function, index + 1)
    return interceptors[index].intercept(next)
  }
}
