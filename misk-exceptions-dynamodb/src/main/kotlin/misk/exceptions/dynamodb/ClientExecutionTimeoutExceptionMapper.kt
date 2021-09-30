package misk.exceptions.dynamodb

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.exceptions.ExceptionMapper
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import javax.inject.Inject

/** Maps ClientExecutionTimeoutException to 503 responses because the exception is concurrency related */
class ClientExecutionTimeoutExceptionMapper @Inject constructor() :
  ExceptionMapper<ClientExecutionTimeoutException> {
  override fun canHandle(th: Throwable): Boolean =
    th is ClientExecutionTimeoutException

  override fun toResponse(th: ClientExecutionTimeoutException): Response<ResponseBody> = Response(
    body = "DynamoDB Resource Contention Exception: $th".toResponseBody(),
    headers = HEADERS,
    statusCode = HTTP_UNAVAILABLE
  )

  private companion object {
    val HEADERS: Headers = listOf(
      "Content-Type" to MediaTypes.TEXT_PLAIN_UTF8
    ).toMap().toHeaders()
  }
}
