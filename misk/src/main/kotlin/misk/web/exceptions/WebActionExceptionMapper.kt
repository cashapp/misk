package misk.web.exceptions

import misk.exceptions.WebActionException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import jakarta.inject.Inject
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import java.net.HttpURLConnection

/**
 * Maps [WebActionException]s into the appropriate status code. [WebActionException]s' response
 * bodies are always returned to the caller.
 */
internal class WebActionExceptionMapper @Inject internal constructor(
  val config: ActionExceptionLogLevelConfig
) : ExceptionMapper<WebActionException> {
  override fun toResponse(th: WebActionException): Response<ResponseBody> {
    return when (th) {
      is UnauthenticatedException -> UNAUTHENTICATED_RESPONSE
      is UnauthorizedException -> UNAUTHORIZED_RESPONSE
      else -> Response(th.responseBody.toResponseBody(), HEADERS, statusCode = th.code)
    }
  }

  override fun toGrpcResponse(th: WebActionException): GrpcErrorResponse {
    return GrpcErrorResponse(th.grpcStatus ?: toGrpcStatus(th.code), "${th.responseBody}\n${th.stackTraceToString()}", th.details)
  }

  override fun loggingLevel(th: WebActionException) =
    if (th.isClientError) config.client_error_level
    else config.server_error_level

  override fun isError(th: WebActionException) = th.isClientError || th.isServerError

  private companion object {
    val HEADERS = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)

    val UNAUTHENTICATED_RESPONSE = Response(
      "unauthenticated".toResponseBody(),
      HEADERS,
      HttpURLConnection.HTTP_UNAUTHORIZED
    )

    val UNAUTHORIZED_RESPONSE = Response(
      "unauthorized".toResponseBody(),
      HEADERS,
      HttpURLConnection.HTTP_FORBIDDEN
    )
  }
}
