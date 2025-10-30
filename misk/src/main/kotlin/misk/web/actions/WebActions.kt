package misk.web.actions

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import misk.ApplicationInterceptor
import misk.Chain
import misk.grpc.GrpcMessageSinkChannel
import misk.grpc.GrpcMessageSourceChannel
import misk.scope.ActionScope
import misk.web.HttpCall
import misk.web.RealChain
import misk.web.ResponseSinkChannel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy

fun WebAction.asChain(
  function: KFunction<*>,
  args: List<Any?>,
  interceptors: List<ApplicationInterceptor>,
  httpCall: HttpCall,
  scope: ActionScope,
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

      return if (!function.isSuspend) {
        function.callBy(argsMap)
      } else {
        // Handle suspending invocation, this includes building out the context to propagate MDC
        // and action scope.
        val context = MDCContext() +
          if (scope.inScope()) {
            scope.asContextElement()
          } else {
            EmptyCoroutineContext
          }

        runBlocking(context) {
          // Build the list of Source and Sink Channels (should only be 0 or 1 of each)
          val grpcMessageSourceChannel = argsMap.values
            .mapNotNull { it as? GrpcMessageSourceChannel<*> }
            .singleOrNull()
          val grpcMessageSinkChannel = argsMap.values
            .mapNotNull { it as? GrpcMessageSinkChannel<*> }
            .singleOrNull()
          val responseSinkChannel = argsMap.values
            .mapNotNull { it as? ResponseSinkChannel<*> }
            .singleOrNull()
          // Launch a coroutine for each Source and Sink Channels to bridge the data
          grpcMessageSourceChannel?.let { launch { it.bridgeFromSource() } }
          grpcMessageSinkChannel?.let { launch { it.bridgeToSink() } }
          responseSinkChannel?.let { launch { it.bridgeToSink() } }
          try {
            (function as? FunctionWithOverrides)?.callSuspendBy(argsMap)
              ?: function.callSuspendBy(argsMap)
          } finally {
            // Once the action is complete, close the send channel and wait for the jobs to finish
            // This blocks any additional sends to the channel, but will allow existing responses in
            // the channel to be read and bridged to the sink
            grpcMessageSinkChannel?.close()
            responseSinkChannel?.close()
          }
        }
      } ?: throw IllegalStateException("Null return from WebAction")
    }
  }
  val realChainInterceptors = interceptors + callFunctionInterceptor
  return RealChain(this, args, realChainInterceptors, function, httpCall, 0)
}
