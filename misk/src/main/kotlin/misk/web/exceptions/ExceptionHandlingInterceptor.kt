package misk.web.exceptions

import com.google.common.util.concurrent.UncheckedExecutionException
import misk.Action
import misk.exceptions.StatusCode
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.logging.getLogger
import misk.logging.log
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import misk.web.ResponseBody
import misk.web.interceptors.RequestLoggingInterceptor
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import mu.KLogger
import okhttp3.Headers
import java.lang.RuntimeException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException
import javax.inject.Inject

/**
 * Converts and logs application and component level dispatch exceptions into the appropriate
 * response format. Allows application and component code to control how exceptions are
 * represented to clients; for example by setting the status code appropriately, or by returning
 * a specialized response format specific to the error. Components can control how exceptions are
 * mapped by installing [ExceptionMapper] via the [ExceptionMapperModule]
 */
class ExceptionHandlingInterceptor(
  private val actionName: String,
  private val mapperResolver: ExceptionMapperResolver
) : NetworkInterceptor {

  override fun intercept(chain: NetworkChain): Response<*> = try {
    chain.proceed(chain.request)
  } catch (th: Throwable) {
    when (th) {
      is InvocationTargetException -> log.info("JAYDEBUGINFO ExceptionHandlingInterceptor#intercept ${th.targetException::class.simpleName}")
    }
    toResponse(th)
  }

  private fun toResponse(th: Throwable): Response<*> {
    log.info(th) { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse" }
    when (th) {
      is UnauthenticatedException ->  {
        log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse is UnauthenticatedException" }
        return UNAUTHENTICATED_RESPONSE
      }
      is UnauthorizedException -> {
        log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse is UnauthorizedException" }
        return UNAUTHORIZED_RESPONSE
      }
      is ExecutionException -> {
        log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse is ExecutionException" }
        return toResponse(th.cause!!)
      }
      is InvocationTargetException -> {
        log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse is InvocationTargetException" }
        return toResponse(th.targetException)
      }
      is UncheckedExecutionException -> {
        log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse is UncheckedExecutionException" }
        return toResponse(th.cause!!)
      }
      else -> {
        val possibleResponse: Response<ResponseBody>? = mapperResolver.mapperFor(th)?.let {
          log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse else\nloggingLevel=${it.loggingLevel(th)} action=$actionName" }
          log.log(it.loggingLevel(th), th) { "exception dispatching to $actionName" }
          it.toResponse(th)
        }
        log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toResponse else\nmapperResolver=${possibleResponse.toString()}" }
        return possibleResponse ?: toInternalServerError(th)
      }
    }
  }

  private fun toInternalServerError(th: Throwable): Response<*> {
    log.info { "JAYDEBUGINFO Entering ExceptionHandlingInterceptor#toInternalServerError" }
    log.info { "JAYDEBUGINFO Entering ExceptionHandlingInterceptor#toInternalServerError: ${th::class.simpleName}" }
    log.info { "JAYDEBUGINFO name: ${log.name}, ${log.underlyingLogger.name} isInfoEnabled: ${log.isInfoEnabled}, ${log.underlyingLogger.isInfoEnabled}"}

    log.info(th) {
      log.info { "JAYDEBUGINFO ExceptionHandlingInterceptor#toInternalServerError Create throwable log" }
      "JAYDEBUGINFO ExceptionHandlingInterceptor#toInternalServerError"
    }
    log.info("JAYDEBUGINFO ExceptionHandlingInterceptor#toInternalServerError Non-lambda log", th)
    getLogger<RequestLoggingInterceptor>().error(RuntimeException(th)) { "unexpected error dispatching to $actionName" }
    return INTERNAL_SERVER_ERROR_RESPONSE
  }

  class Factory @Inject internal constructor(
    private val mapperResolver: ExceptionMapperResolver
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action) = ExceptionHandlingInterceptor(action.name, mapperResolver)
  }

  private companion object {
    val log: KLogger by lazy { getLogger<ExceptionHandlingInterceptor>() }

    val INTERNAL_SERVER_ERROR_RESPONSE = Response("internal server error".toResponseBody(),
        Headers.of(listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap()),
        StatusCode.INTERNAL_SERVER_ERROR.code
    )

    val UNAUTHENTICATED_RESPONSE = Response("unauthenticated".toResponseBody(),
        Headers.of(listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap()),
        StatusCode.UNAUTHENTICATED.code
    )

    val UNAUTHORIZED_RESPONSE = Response("unauthorized".toResponseBody(),
        Headers.of(listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap()),
        StatusCode.FORBIDDEN.code
    )
  }
}
