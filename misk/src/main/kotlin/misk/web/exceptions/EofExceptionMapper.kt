package misk.web.exceptions

import misk.exceptions.StatusCode
import misk.web.Response
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import org.eclipse.jetty.io.EofException
import org.slf4j.event.Level
import javax.inject.Inject

internal class EofExceptionMapper @Inject internal constructor() : ExceptionMapper<EofException> {
  override fun toResponse(th: EofException) = CLIENT_CLOSED_REQUEST

  override fun canHandle(th: Throwable): Boolean = th is EofException

  override fun loggingLevel(th: EofException): Level = Level.INFO

  companion object {
    val CLIENT_CLOSED_REQUEST = Response("client closed request".toResponseBody(),
        Headers.headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
        StatusCode.CLIENT_CLOSED_REQUEST.code
    )
  }
}
