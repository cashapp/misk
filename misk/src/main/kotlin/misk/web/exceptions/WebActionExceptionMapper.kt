package misk.web.exceptions

import misk.exceptions.WebActionException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import javax.inject.Inject

/**
 * Maps [WebActionException]s into the appropriate status code. [WebActionException]s' response
 * bodies are always returned to the caller.
 */
internal class WebActionExceptionMapper @Inject internal constructor(
  val config: ActionExceptionLogLevelConfig
) : ExceptionMapper<WebActionException> {
  override fun toResponse(th: WebActionException): Response<ResponseBody> {
    return Response(th.responseBody.toResponseBody(), HEADERS, statusCode = th.code)
  }

  override fun toGrpcResponse(th: WebActionException): GrpcErrorResponse {
    return GrpcErrorResponse(th.grpcStatus ?: toGrpcStatus(th.code), "${th.responseBody}\n${th.stackTraceToString()}", th.details)
  }

  override fun canHandle(th: Throwable): Boolean = th is WebActionException

  override fun loggingLevel(th: WebActionException) =
    if (th.isClientError) config.client_error_level
    else config.server_error_level

  private companion object {
    val HEADERS: Headers =
      listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap().toHeaders()
  }
}
