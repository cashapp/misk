package misk.web.actions

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.runBlocking
import misk.ApplicationInterceptor
import misk.Chain
import misk.web.HttpCall
import misk.web.RealChain
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy

fun WebAction.asChain(
  function: KFunction<*>,
  args: List<Any?>,
  interceptors: List<ApplicationInterceptor>,
  httpCall: HttpCall
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

      return if (function.isSuspend) {
        val coroutineName = CoroutineName(
          "WebAction: ${this@asChain::class.simpleName}.${function.name}"
        )
        runBlocking(coroutineName) {
          function.callSuspendBy(argsMap)
        }
      } else {
        function.callBy(argsMap)
      } ?: throw IllegalStateException("Null return from WebAction")
    }
  }
  val realChainInterceptors = interceptors + callFunctionInterceptor
  return RealChain(this, args, realChainInterceptors, function, httpCall, 0)
}
