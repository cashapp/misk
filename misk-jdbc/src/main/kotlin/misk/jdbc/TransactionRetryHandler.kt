package misk.jdbc

import misk.backoff.ExponentialBackoff
import misk.logging.getLogger
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.time.Duration

/**
 * Common transaction retry logic for database transacters.
 * 
 * This class provides a unified way to handle retryable database exceptions across
 * different persistence technologies (Hibernate, JOOQ, etc.).
 */
class TransactionRetryHandler(
  private val qualifierName: String = "database",
  private val exceptionClassifier: ExceptionClassifier = DefaultExceptionClassifier()
) {
  
  /**
   * Executes a block with retry logic for transient database failures.
   */
  fun <T> executeWithRetries(
    maxAttempts: Int = 3,
    minRetryDelayMillis: Long = 100,
    maxRetryDelayMillis: Long = 500,
    retryJitterMillis: Long = 400,
    block: () -> T
  ): T {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    
    val backoff = ExponentialBackoff(
      Duration.ofMillis(minRetryDelayMillis),
      Duration.ofMillis(maxRetryDelayMillis),
      Duration.ofMillis(retryJitterMillis)
    )
    var attempt = 0

    while (true) {
      try {
        attempt++
        val result = block()

        if (attempt > 1) {
          logger.info { "retried $qualifierName transaction succeeded (attempt $attempt)" }
        }

        return result
      } catch (e: Exception) {
        if (!exceptionClassifier.isRetryable(e)) throw e

        if (attempt >= maxAttempts) {
          logger.info {
            "$qualifierName recoverable transaction exception (attempt $attempt), no more attempts"
          }
          throw e
        }

        val sleepDuration = backoff.nextRetry()
        logger.info(e) {
          "$qualifierName recoverable transaction exception " +
            "(attempt $attempt), will retry after a $sleepDuration delay"
        }

        if (!sleepDuration.isZero) {
          Thread.sleep(sleepDuration.toMillis())
        }
      }
    }
  }

  companion object {
    private val logger = getLogger<TransactionRetryHandler>()
  }
}

/**
 * Interface for classifying exceptions as retryable or non-retryable.
 */
interface ExceptionClassifier {
  fun isRetryable(throwable: Throwable): Boolean
}

/**
 * Default exception classifier that handles common database retry scenarios.
 */
open class DefaultExceptionClassifier : ExceptionClassifier {
  
  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is SQLRecoverableException,
      is SQLTransientException -> true
      is SQLException -> if (isMessageRetryable(th)) true else isCauseRetryable(th)
      else -> isCauseRetryable(th)
    }
  }

  protected fun isMessageRetryable(th: SQLException): Boolean =
    isConnectionClosed(th) ||
      isVitessTransactionNotFound(th) ||
      isCockroachRestartTransaction(th) ||
      isTidbWriteConflict(th)

  /**
   * This is thrown as a raw SQLException from Hikari even though it is most certainly a recoverable exception.
   * See com/zaxxer/hikari/pool/ProxyConnection.java:493
   */
  protected fun isConnectionClosed(th: SQLException): Boolean = 
    th.message.equals("Connection is closed")

  /**
   * We get this error as a MySQLQueryInterruptedException when a tablet gracefully terminates, we just need to retry
   * the transaction and the new primary should handle it.
   *
   * ```
   * vttablet: rpc error: code = Aborted desc = transaction 1572922696317821557:
   * not found (CallerID: )
   * ```
   */
  protected fun isVitessTransactionNotFound(th: SQLException): Boolean {
    val message = th.message
    return message != null &&
      message.contains("vttablet: rpc error") &&
      message.contains("code = Aborted") &&
      message.contains("transaction") &&
      message.contains("not found")
  }

  /**
   * "Messages with the error code 40001 and the string restart transaction indicate that a transaction failed because
   * it conflicted with another concurrent or recent transaction accessing the same data. The transaction needs to be
   * retried by the client." https://www.cockroachlabs.com/docs/stable/common-errors.html#restart-transaction
   */
  protected fun isCockroachRestartTransaction(th: SQLException): Boolean {
    val message = th.message
    return th.errorCode == 40001 && message != null && message.contains("restart transaction")
  }

  /**
   * "Transactions in TiKV encounter write conflicts". This can happen when optimistic transaction mode is on. Conflicts
   * are detected during transaction commit https://docs.pingcap.com/tidb/dev/tidb-faq#error-9007-hy000-write-conflict
   */
  protected fun isTidbWriteConflict(th: SQLException): Boolean {
    return th.errorCode == 9007
  }

  protected fun isCauseRetryable(th: Throwable): Boolean = 
    th.cause?.let { isRetryable(it) } ?: false
}

/**
 * Exception that can be thrown by application code to force a transaction retry.
 * This is commonly used in Hibernate-based applications.
 */
class RetryTransactionException(message: String? = null, cause: Throwable? = null) : 
  RuntimeException(message, cause)
