package misk.exceptions.dynamodb

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.exceptions.ExceptionMapper
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import jakarta.inject.Inject

/** Maps ClientExecutionTimeoutException to 503 responses because the exception is concurrency related */
class ClientExecutionTimeoutExceptionMapper @Inject constructor() :
  ExceptionMapper<ClientExecutionTimeoutException> {
  override fun toResponse(th: ClientExecutionTimeoutException): Response<ResponseBody> = Response(
    body = "DynamoDB Resource Contention Exception: $th".toResponseBody(),
    headers = HEADERS,
    statusCode = HTTP_UNAVAILABLE
  )

  private companion object {
    val HEADERS = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
  }
}
