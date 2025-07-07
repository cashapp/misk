package misk.exceptions.dynamodb

import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException
import misk.web.Response
import misk.web.ResponseBody
import misk.web.exceptions.ExceptionMapper
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import org.slf4j.event.Level
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import jakarta.inject.Inject

/** Maps certain TransactionCanceledExceptionMapper to 503 responses when the exception is concurrency related */
class TransactionCanceledExceptionMapper @Inject constructor() :
  ExceptionMapper<TransactionCanceledException> {
  /**
   * Certain [TransactionCanceledException] codes are reflective of resource contention exceptions
   * solvable by retrying the transaction. In order to not have these exceptions show up as generic
   * 500 exceptions, they are mapped and thrown as Service Unavailable 503 exceptions.
   *
   * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/model/TransactionCanceledException.html
   */
  private val resourceContentionCancellationReasonCodes = setOf(
    "ConditionalCheckFailed",
    "TransactionConflict"
  )

  private fun TransactionCanceledException.isFromResourceContention() = cancellationReasons.any {
    resourceContentionCancellationReasonCodes.contains(it.code)
  }

  override fun toResponse(th: TransactionCanceledException): Response<ResponseBody> = if (
    th.isFromResourceContention()
  ) {
    val message = "DynamoDB Resource Contention Exception: $th"
    Response(
      body = message.toResponseBody(), headers = HEADERS,
      statusCode = HTTP_UNAVAILABLE
    )
  } else {
    val message = "Internal server error: $th"
    Response(
      body = message.toResponseBody(), headers = HEADERS,
      statusCode = HTTP_INTERNAL_ERROR
    )
  }

  override fun loggingLevel(th: TransactionCanceledException): Level = if (
    th.isFromResourceContention()
  ) {
    Level.INFO
  } else {
    Level.ERROR
  }

  private companion object {
    val HEADERS = headersOf("Content-Type", MediaTypes.TEXT_PLAIN_UTF8)
  }
}
