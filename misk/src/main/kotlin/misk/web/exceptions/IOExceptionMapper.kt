package misk.web.exceptions

import misk.web.Response
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import org.slf4j.event.Level
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject

/**
 * Maps [IOException]s to HTTP 500.
 */
internal class IOExceptionMapper @Inject internal constructor() : ExceptionMapper<IOException> {
  override fun toResponse(th: IOException) = INTERNAL_SERVER_ERROR_RESPONSE

  override fun loggingLevel(th: IOException): Level = Level.WARN

  companion object {
    val INTERNAL_SERVER_ERROR_RESPONSE = Response(
      "internal server error".toResponseBody(),
      headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
      HttpURLConnection.HTTP_INTERNAL_ERROR
    )
  }
}
