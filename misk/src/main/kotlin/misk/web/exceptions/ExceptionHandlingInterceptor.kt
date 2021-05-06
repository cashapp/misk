package misk.web.exceptions

import com.google.common.util.concurrent.UncheckedExecutionException
import misk.Action
import misk.exceptions.StatusCode
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.toHeaders
import wisp.logging.getLogger
import wisp.logging.log
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject

/**
 * Converts and logs application and component level dispatch exceptions into the appropriate
 * response format. Allows application and component code to control how exceptions are
 * represented to clients; for example by setting the status code appropriately, or by returning
 * a specialized response format specific to the error. Components can control how exceptions are
 * mapped by installing [ExceptionMapper] via the [ExceptionMapperModule]
 *
 * TODO(isabel): Set the response body in a ThreadLocal to log in [RequestLoggingInterceptor]
 */
class ExceptionHandlingInterceptor(
  private val actionName: String,
  private val mapperResolver: ExceptionMapperResolver
) : NetworkInterceptor {

  override fun intercept(chain: NetworkChain) {
    try {
      chain.proceed(chain.httpCall)
    } catch (th: Throwable) {
      val response = toResponse(th)
      chain.httpCall.statusCode = response.statusCode
      chain.httpCall.takeResponseBody()?.use { sink ->
        chain.httpCall.addResponseHeaders(response.headers)
        (response.body as ResponseBody).writeTo(sink)
      }
    }
  }

  private fun toResponse(th: Throwable): Response<*> = when (th) {
    is UnauthenticatedException -> UNAUTHENTICATED_RESPONSE
    is UnauthorizedException -> UNAUTHORIZED_RESPONSE
    is InvocationTargetException -> toResponse(th.targetException)
    is UncheckedExecutionException -> toResponse(th.cause!!)
    else -> mapperResolver.mapperFor(th)?.let {
      log.log(it.loggingLevel(th), th) { "exception dispatching to $actionName" }
      it.toResponse(th)
    } ?: toInternalServerError(th)
  }

  private fun toInternalServerError(th: Throwable): Response<*> {
    log.error(th) { "unexpected error dispatching to $actionName" }
    return INTERNAL_SERVER_ERROR_RESPONSE
  }

  class Factory @Inject internal constructor(
    private val mapperResolver: ExceptionMapperResolver
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action) = ExceptionHandlingInterceptor(action.name, mapperResolver)
  }

  private companion object {
    val log = getLogger<ExceptionHandlingInterceptor>()

    val INTERNAL_SERVER_ERROR_RESPONSE = Response(
      "internal server error".toResponseBody(),
      listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap().toHeaders(),
      StatusCode.INTERNAL_SERVER_ERROR.code
    )

    val UNAUTHENTICATED_RESPONSE = Response(
      "unauthenticated".toResponseBody(),
      listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap().toHeaders(),
      StatusCode.UNAUTHENTICATED.code
    )

    val UNAUTHORIZED_RESPONSE = Response(
      "unauthorized".toResponseBody(),
      listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap().toHeaders(),
      StatusCode.FORBIDDEN.code
    )
  }
}
