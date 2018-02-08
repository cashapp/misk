package misk.web.exceptions

import misk.exceptions.ActionException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.marshal.StringResponseBody
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import org.slf4j.event.Level

/**
 * Maps [ActionException]s into the appropriate status code. [ActionException]s corresponding
 * to client-errors (bad requests, resource not found, etc) are returned with full messages
 * allowing the client to deterine what went wrong; exceptions representing server errors
 * are returned with just a status code and minimal messaging, to avoid leaking internal
 * implementation details and possible vulnerabilities
 */
internal class ActionExceptionMapper : ExceptionMapper<ActionException> {
    override fun toResponse(th: ActionException): Response<ResponseBody> {
        val message = if (th.statusCode.isClientError) th.message ?: th.statusCode.name
        else th.statusCode.name
        return Response(StringResponseBody(message), HEADERS, statusCode = th.statusCode.code)
    }

    override fun canHandle(th: Throwable): Boolean = th is ActionException

    override fun loggingLevel(th: ActionException) =
            if (th.statusCode.isClientError) Level.WARN
            else Level.ERROR

    private companion object {
        val HEADERS: Headers =
                Headers.of(listOf("Content-Type" to MediaTypes.TEXT_PLAIN_UTF8).toMap())
    }
}

