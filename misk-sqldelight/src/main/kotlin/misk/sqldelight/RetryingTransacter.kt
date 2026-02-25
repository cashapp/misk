package misk.sqldelight

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import java.time.Duration
import misk.backoff.ExponentialBackoff
import misk.jdbc.DataSourceType
import misk.jdbc.retry.RetryDefaults
import misk.logging.getLogger

private val logger = getLogger<RetryingTransacter>()

// NB: all options should be immutable types as copy() is shallow.
data class TransacterOptions
@JvmOverloads
constructor(
  val maxRetries: Int = RetryDefaults.MAX_RETRIES,
  val minRetryDelayMillis: Long = RetryDefaults.MIN_RETRY_DELAY_MILLIS,
  val maxRetryDelayMillis: Long = RetryDefaults.MAX_RETRY_DELAY_MILLIS,
  val retryJitterMillis: Long = RetryDefaults.RETRY_JITTER_MILLIS,
)

abstract class RetryingTransacter
@JvmOverloads
constructor(
  private val delegate: Transacter,
  val options: TransacterOptions = TransacterOptions(),
  dataSourceType: DataSourceType? = null,
) : Transacter {

  private val exceptionClassifier = SqlDelightExceptionClassifier(dataSourceType)

  private val inTransaction =
    object : ThreadLocal<Boolean>() {
      override fun initialValue(): Boolean = false
    }

  override fun transaction(noEnclosing: Boolean, body: TransactionWithoutReturn.() -> Unit) = retryWithWork {
    delegate.transaction(noEnclosing, body)
  }

  override fun <R> transactionWithResult(noEnclosing: Boolean, bodyWithReturn: TransactionWithReturn<R>.() -> R): R =
    retryWithWork {
      delegate.transactionWithResult(noEnclosing, bodyWithReturn)
    }

  private fun <T> retryWithWork(work: () -> T): T {
    val outermostTransaction: Boolean
    if (inTransaction.get()) {
      outermostTransaction = false
    } else {
      outermostTransaction = true
      inTransaction.set(true)
    }

    try {
      val backoff =
        ExponentialBackoff(
          Duration.ofMillis(options.minRetryDelayMillis),
          Duration.ofMillis(options.maxRetryDelayMillis),
          Duration.ofMillis(options.retryJitterMillis),
        )
      var attempt = 0
      while (true) {
        try {
          attempt++
          val result = work()

          if (attempt > 1) {
            logger.info { "retried transaction succeeded (attempt $attempt)" }
          }

          return result
        } catch (e: Exception) {
          if (!(exceptionClassifier.isRetryable(e) && outermostTransaction)) throw e

          if (attempt >= options.maxRetries) {
            logger.info { "recoverable transaction exception " + "(attempt $attempt), no more attempts" }

            throw e
          }

          val sleepDuration = backoff.nextRetry()
          logger.info(e) {
            "recoverable transaction exception " + "(attempt $attempt), will retry after a $sleepDuration delay"
          }

          if (!sleepDuration.isZero) {
            Thread.sleep(sleepDuration.toMillis())
          }
        }
      }
    } finally {
      if (outermostTransaction) {
        inTransaction.set(false)
      }
    }
  }
}
