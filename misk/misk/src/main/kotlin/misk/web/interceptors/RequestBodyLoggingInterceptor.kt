package misk.web.interceptors

import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.logging.getLogger
import misk.scope.ActionScoped
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

private val logger = getLogger<RequestBodyLoggingInterceptor>()

/**
 * Stores request and response information for an action in a ThreadLocal, to be logged
 * in [RequestLoggingInterceptor]
 *
 * Timing information doesn't count time writing the response to the remote client.
 */
class RequestBodyLoggingInterceptor @Inject internal constructor(
  private val action: Action,
  private val caller: ActionScoped<MiskCaller?>,
  private val bodyCapture: RequestResponseCapture
) : ApplicationInterceptor {

  @Singleton
  class Factory @Inject internal constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    private val bodyCapture: RequestResponseCapture
  ) : ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor? {
      val logRequestResponse = action.function.findAnnotation<LogRequestResponse>() ?: return null
      require(0.0 <= logRequestResponse.bodySampling && logRequestResponse.bodySampling <= 1.0) {
        "${action.name} @LogRequestResponse sampling must be in the range (0.0, 1.0]"
      }
      require(0.0 <= logRequestResponse.errorBodySampling && logRequestResponse.errorBodySampling <= 1.0) {
        "${action.name} @LogRequestResponse sampling must be in the range (0.0, 1.0]"
      }
      if (logRequestResponse.bodySampling == 0.0 && logRequestResponse.errorBodySampling == 0.0) {
        return null
      }

      return RequestBodyLoggingInterceptor(
        action,
        caller,
        bodyCapture
      )
    }
  }

  override fun intercept(chain: Chain): Any {
    val principal = caller.get()?.principal ?: "unknown"

    bodyCapture.set(RequestResponseBody(chain.args, null))

    try {
      val result = chain.proceed(chain.args)
      bodyCapture.set(RequestResponseBody(chain.args, result))
      return result
    } catch (t: Throwable) {
      logger.info { "${action.name} principal=$principal failed" }
      throw t
    }
  }
}

internal class RequestResponseCapture @Inject constructor() {
  companion object {
    private val capture = ThreadLocal<RequestResponseBody>()
  }

  fun get(): RequestResponseBody {
    return capture.get()
  }

  fun set(value: RequestResponseBody) {
    capture.set(value)
  }

  fun clear() {
    return capture.remove()
  }
}

internal data class RequestResponseBody(val request: Any?, val response: Any?)