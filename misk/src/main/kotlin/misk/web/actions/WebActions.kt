package misk.web.actions

import com.google.common.collect.Lists
import misk.Chain
import misk.ApplicationInterceptor
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealChain
import misk.web.RealNetworkChain
import misk.web.Request
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

fun WebAction.asNetworkChain(
  function: KFunction<*>,
  request: Request,
  vararg _networkInterceptors: NetworkInterceptor
): NetworkChain =
    RealNetworkChain(this, request, _networkInterceptors.toList(), function, 0)

fun WebAction.asChain(
  function: KFunction<*>,
  args: List<Any?>,
  vararg _interceptors: ApplicationInterceptor
): Chain {
  val interceptors = Lists.newArrayList(_interceptors.iterator())
  interceptors.add(object : ApplicationInterceptor {
    override fun intercept(chain: Chain): Any {
      val parameterMap = LinkedHashMap<KParameter, Any?>()
      parameterMap[function.parameters.first()] = chain.action
      for (i in 1 until function.parameters.size) {
        val param = function.parameters[i]
        val arg = chain.args[i - 1]
        if (param.isOptional && arg == null) {
          continue
        }
        parameterMap[param] = arg
      }
      return function.callBy(parameterMap)
          ?: throw IllegalStateException("Null return from WebAction")
    }
  })
  return RealChain(this, args, interceptors, function, 0)
}
