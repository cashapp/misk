package misk.web.actions

import misk.ApplicationInterceptor
import misk.Chain
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealChain
import misk.web.RealNetworkChain
import misk.web.Request
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

internal fun WebAction.asNetworkChain(
  function: KFunction<*>,
  request: Request,
  networkInterceptors: List<NetworkInterceptor>
): NetworkChain = RealNetworkChain(this, request, networkInterceptors, function, 0)

fun WebAction.asChain(
  function: KFunction<*>,
  args: List<Any?>,
  interceptors: List<ApplicationInterceptor>
): Chain {
  val callFunctionInterceptor = object : ApplicationInterceptor {
    override fun intercept(chain: Chain): Any {
      val argsMap = mutableMapOf<KParameter, Any?>()
      argsMap[function.parameters.first()] = chain.action
      for (i in 1 until function.parameters.size) {
        val param = function.parameters[i]
        val arg = chain.args[i - 1]
        if (param.isOptional && arg == null) {
          continue
        }
        argsMap[param] = arg
      }
      return function.callBy(argsMap)
          ?: throw IllegalStateException("Null return from WebAction")
    }
  }
  val realChainInterceptors = interceptors + callFunctionInterceptor
  return RealChain(this, args, realChainInterceptors, function, 0)
}
