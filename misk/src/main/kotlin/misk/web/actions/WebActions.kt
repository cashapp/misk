package misk.web.actions

import com.google.common.collect.Lists
import misk.Chain
import misk.Interceptor
import misk.web.RealChain
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

fun WebAction.asChain(
    function: KFunction<*>,
    args: List<Any?>,
    vararg _interceptors: Interceptor
): Chain {
  val interceptors = Lists.newArrayList(_interceptors.iterator())
  interceptors.add(object : Interceptor {
    override fun intercept(chain: Chain): Any? {
      val parameterMap = LinkedHashMap<KParameter, Any?>()
      parameterMap.put(function.parameters.first(), chain.action)
      for (i in 1 until function.parameters.size) {
        val param = function.parameters.get(i)
        val arg = chain.args.get(i - 1)
        if (param.isOptional && arg == null) {
          continue
        }
        parameterMap.put(param, arg)
      }
      return function.callBy(parameterMap)
    }
  })
  return RealChain(this, args, interceptors, function, 0)
}
