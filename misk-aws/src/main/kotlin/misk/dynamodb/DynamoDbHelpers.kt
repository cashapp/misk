package misk.dynamodb

import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import java.time.Duration
import misk.backoff.Backoff
import misk.backoff.DontRetryException
import misk.backoff.ExponentialBackoff
import misk.backoff.retry

object DynamoDbHelpers {
  val DEFAULT_BACKOFF = ExponentialBackoff(
      baseDelay = Duration.ofMillis(10),
      maxDelay = Duration.ofMillis(500),
      jitter = Duration.ofMillis(5)
  )

  /**
   * Retries any [AmazonDynamoDBException] or [PleaseRetryException]. [DontRetryException] will
   * not be retried.
   */
  fun <T> dynamoDbRetry(
    retries: Int = 3,
    backoff: Backoff = DEFAULT_BACKOFF,
    action: (Int) -> T
  ): T {
    return retry(retries, backoff) { retryNumber: Int ->
      try {
        action(retryNumber)
      } catch (e: AmazonDynamoDBException) {
        throw e
      } catch (e: PleaseRetryException) {
        throw e
      } catch (e: Exception) {
        throw DontRetryException(e.message ?: "Non-DynamoDb exception thrown", e.cause)
      }
    }
  }
}

class PleaseRetryException(message: String, cause: Throwable?): Exception(message, cause)
