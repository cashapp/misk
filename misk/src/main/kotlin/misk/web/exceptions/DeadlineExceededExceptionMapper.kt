package misk.web.exceptions

import com.squareup.wire.GrpcStatus.Companion.DEADLINE_EXCEEDED
import jakarta.inject.Inject
import misk.client.HTTP_GATEWAY_TIMEOUT
import misk.web.Response
import misk.web.mediatype.MediaTypes
import misk.web.requestdeadlines.DeadlineExceededException
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import org.slf4j.event.Level

/**
 * Maps [DeadlineExceededException] to HTTP 504 Gateway Timeout responses. This indicates the server was acting as a
 * gateway and didn't receive a timely response.
 */
internal class DeadlineExceededExceptionMapper @Inject internal constructor() :
  ExceptionMapper<DeadlineExceededException> {
  override fun loggingLevel(th: DeadlineExceededException) = Level.WARN

  override fun toResponse(th: DeadlineExceededException) = DEADLINE_EXCEEDED_RESPONSE

  override fun toGrpcResponse(th: DeadlineExceededException): GrpcErrorResponse? {
    return GrpcErrorResponse(DEADLINE_EXCEEDED, th.message)
  }

  companion object {
    val DEADLINE_EXCEEDED_RESPONSE =
      Response(
        "deadline exceeded".toResponseBody(),
        headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8),
        HTTP_GATEWAY_TIMEOUT,
      )
  }
}
