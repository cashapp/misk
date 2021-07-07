package misk.web.exceptions

import misk.exceptions.WebActionException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import org.apache.hc.core5.http.impl.EnglishReasonPhraseCatalog
import java.util.Locale
import javax.inject.Inject

/**
 * Maps [WebActionException]s into the appropriate status code. [WebActionException]s corresponding
 * to client-errors (bad requests, resource not found, etc) are returned with full messages
 * allowing the client to determine what went wrong; exceptions representing server errors
 * are returned with just a status code and minimal messaging, to avoid leaking internal
 * implementation details and possible vulnerabilities.
 */
internal class WebActionExceptionMapper @Inject internal constructor(
  val config: ActionExceptionLogLevelConfig
) : ExceptionMapper<WebActionException> {
  override fun toResponse(th: WebActionException): Response<ResponseBody> {
    val message: String = if (th.isClientError) th.responseBody else friendlyMessage(th.code)
    return Response(message.toResponseBody(), HEADERS, statusCode = th.code)
  }

  override fun toGrpcResponse(th: WebActionException): GrpcErrorResponse {
    return GrpcErrorResponse(toGrpcStatus(th.code), th.responseBody)
  }

  private fun friendlyMessage(code: Int): String =
    EnglishReasonPhraseCatalog.INSTANCE.getReason(code, Locale.ENGLISH)

  override fun canHandle(th: Throwable): Boolean = th is WebActionException

  override fun loggingLevel(th: WebActionException) =
    if (th.isClientError) config.client_error_level
    else config.server_error_level

  private companion object {
    val HEADERS: Headers =
      listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap().toHeaders()
  }
}
