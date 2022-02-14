package misk.web.exceptions

import misk.exceptions.ClientClosedRequestException
import misk.web.Response
import misk.web.extractors.RequestBodyException
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import org.slf4j.event.Level
import javax.inject.Inject

class RequestBodyExceptionMapper @Inject internal constructor() : ExceptionMapper<RequestBodyException> {
  override fun canHandle(th: Throwable) = th is RequestBodyException

  override fun loggingLevel(th: RequestBodyException) = Level.INFO

  override fun toResponse(th: RequestBodyException) = CLIENT_CLOSED_REQUEST

  companion object {
    val CLIENT_CLOSED_REQUEST = Response(
      "client closed request".toResponseBody(),
      Headers.headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
      ClientClosedRequestException().code
    )
  }

}
