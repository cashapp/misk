package misk.sqldelight

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.OptimisticLockException
import misk.backoff.ExponentialBackoff
import wisp.logging.getLogger
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.time.Duration

private val logger = getLogger<RetryingTransacter>()

// NB: all options should be immutable types as copy() is shallow.
data class TransacterOptions @JvmOverloads constructor(
  val maxAttempts: Int = 3,
  val minRetryDelayMillis: Long = 100,
  val maxRetryDelayMillis: Long = 500,
  val retryJitterMillis: Long = 400,
)

abstract class RetryingTransacter @JvmOverloads constructor(
  private val delegate: Transacter,
  val options: TransacterOptions = TransacterOptions()
) : Transacter {

  private val inTransaction = object : ThreadLocal<Boolean>() {
    override fun initialValue(): Boolean = false
  }

  override fun transaction(noEnclosing: Boolean,
    body: TransactionWithoutReturn.() -> Unit) = retryWithWork {
      delegate.transaction(noEnclosing, body)
    }

  override fun <R> transactionWithResult(
    noEnclosing: Boolean,
    bodyWithReturn: TransactionWithReturn<R>.() -> R
  ): R = retryWithWork {
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

    val backoff = ExponentialBackoff(
      Duration.ofMillis(options.minRetryDelayMillis),
      Duration.ofMillis(options.maxRetryDelayMillis),
      Duration.ofMillis(options.retryJitterMillis)
    )
    var attempt = 0
    while (true) {
      try {
        attempt++
        val result = work()

        if (attempt > 1) {
          logger.info {
            "retried transaction succeeded (attempt $attempt)"
          }
        }

        if (outermostTransaction) {
          inTransaction.set(false)
        }

        return result
      } catch (e: Exception) {
        if (!(isRetryable(e) && outermostTransaction)) throw e

        if (attempt >= options.maxAttempts) {
          logger.info {
            "recoverable transaction exception " +
              "(attempt $attempt), no more attempts"
          }

          if (outermostTransaction) {
            inTransaction.set(false)
          }

          throw e
        }

        val sleepDuration = backoff.nextRetry()
        logger.info(e) {
          "recoverable transaction exception " +
            "(attempt $attempt), will retry after a $sleepDuration delay"
        }

        if (!sleepDuration.isZero) {
          Thread.sleep(sleepDuration.toMillis())
        }
      }
    }
  }
}

private fun isRetryable(th: Throwable): Boolean {
  return when (th) {
    is SQLRecoverableException,
    is SQLTransientException,
    is OptimisticLockException -> true
    is SQLException -> if (isMessageRetryable(th)) true else isCauseRetryable(th)
    else -> isCauseRetryable(th)
  }
}

private fun isCauseRetryable(th: Throwable) = th.cause?.let { isRetryable(it) } ?: false

private fun isMessageRetryable(th: SQLException) = isConnectionClosed(th)

/**
 * This is thrown as a raw SQLException from Hikari even though it is most certainly a
 * recoverable exception.
 * See com/zaxxer/hikari/pool/ProxyConnection.java:493
 */
private fun isConnectionClosed(th: SQLException) =
  th.message.equals("Connection is closed")
