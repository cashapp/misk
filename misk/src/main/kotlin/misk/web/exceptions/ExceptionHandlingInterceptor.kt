package misk.web.exceptions

import com.google.common.util.concurrent.UncheckedExecutionException
import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.exceptions.StatusCode
import misk.logging.getLogger
import misk.logging.log
import misk.web.Response
import misk.web.marshal.StringResponseBody
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
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
    toResponse(th)
  }

  private fun toResponse(th: Throwable): Response<*> = when (th) {
    is ExecutionException -> toResponse(th.cause!!)
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

    val INTERNAL_SERVER_ERROR_RESPONSE = Response(StringResponseBody("internal server error"),
        Headers.of(listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap()),
        StatusCode.INTERNAL_SERVER_ERROR.code
    )
  }
}
