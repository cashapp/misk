package misk.web.exceptions

import com.squareup.moshi.JsonDataException
import jakarta.inject.Inject
import misk.web.Response
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import org.slf4j.event.Level
import java.net.HttpURLConnection

/**
 * Maps [JsonDataException] to HTTP 400 Bad Request.
 *
 * Moshi throws JsonDataException when the JSON structure is valid but the data doesn't match
 * the expected schema (e.g., wrong types, missing required fields).
 */
internal class JsonDataExceptionMapper @Inject internal constructor() : ExceptionMapper<JsonDataException> {
  override fun toResponse(th: JsonDataException) = BAD_REQUEST_RESPONSE

  override fun loggingLevel(th: JsonDataException): Level = Level.INFO

  companion object {
    val BAD_REQUEST_RESPONSE = Response(
      "bad request".toResponseBody(),
      headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
      HttpURLConnection.HTTP_BAD_REQUEST
    )
  }
}
