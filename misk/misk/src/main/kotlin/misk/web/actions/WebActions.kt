package misk.web.actions

import com.google.common.collect.Lists
import misk.Chain
import misk.Interceptor
import misk.web.RealChain
import kotlin.reflect.KFunction

fun WebAction.asChain(
  function: KFunction<*>,
  args: List<Any?>,
  vararg _interceptors: Interceptor
): Chain {
  val interceptors = Lists.newArrayList(_interceptors.iterator())
  interceptors.add(object : Interceptor {
    override fun intercept(chain: Chain): Any? {
      return function.call(chain.action, *chain.args.toTypedArray())
    }
  })
  return RealChain(this, args, interceptors, function, 0)
}
