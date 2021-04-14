package misk.web.exceptions

import misk.exceptions.ActionException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import org.slf4j.event.Level
import wisp.config.Config
import javax.inject.Inject

/**
 * Maps [ActionException]s into the appropriate status code. [ActionException]s corresponding
 * to client-errors (bad requests, resource not found, etc) are returned with full messages
 * allowing the client to determine what went wrong; exceptions representing server errors
 * are returned with just a status code and minimal messaging, to avoid leaking internal
 * implementation details and possible vulnerabilities
 */
@Deprecated("Superseded by WebActionExceptionMapper")
internal class ActionExceptionMapper @Inject internal constructor(
  val config: ActionExceptionLogLevelConfig
) : ExceptionMapper<ActionException> {
  override fun toResponse(th: ActionException): Response<ResponseBody> {
    val message = if (th.statusCode.isClientError) th.message ?: th.statusCode.name
    else th.statusCode.name
    return Response(message.toResponseBody(), HEADERS, statusCode = th.statusCode.code)
  }

  override fun canHandle(th: Throwable): Boolean = th is ActionException

  override fun loggingLevel(th: ActionException) =
    if (th.statusCode.isClientError) config.client_error_level
    else config.server_error_level

  private companion object {
    val HEADERS: Headers =
      listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap().toHeaders()
  }
}

/**
 * Configures the log [Level] for an ActionException.
 *
 * @property client_error_level the level used for 4xx error codes
 * @property server_error_level the level used for 5xx error codes
 */
data class ActionExceptionLogLevelConfig(
  val client_error_level: Level = Level.WARN,
  val server_error_level: Level = Level.ERROR
) : Config
