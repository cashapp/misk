package misk.jdbc.retry

import misk.jdbc.DataSourceType
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException

/**
 * Default exception classifier that handles common SQL-level retryable exceptions.
 *
 * This classifier handles:
 * - SQLRecoverableException, SQLTransientException: Standard JDBC recoverable exceptions
 * - RetryTransactionException: Explicit retry request
 * - SQLException with specific messages: Connection closed, Vitess transaction not found,
 *   CockroachDB restart transaction, TiDB write conflict
 *
 * Subclasses can extend this to add ORM-specific exception handling.
 *
 * @param dataSourceType The type of database, used to enable database-specific retry logic
 */
open class DefaultExceptionClassifier @JvmOverloads constructor(
  private val dataSourceType: DataSourceType? = null
) : ExceptionClassifier {

  override fun isRetryable(th: Throwable): Boolean {
    return when (th) {
      is RetryTransactionException,
      is SQLRecoverableException,
      is SQLTransientException -> true
      is SQLException -> isMessageRetryable(th) || isCauseRetryable(th)
      else -> isCauseRetryable(th)
    }
  }

  private fun isMessageRetryable(th: SQLException) =
    isConnectionClosed(th) || isDatabaseSpecificRetryable(th)

  /**
   * This is thrown as a raw SQLException from Hikari even though it is most certainly a
   * recoverable exception. See com/zaxxer/hikari/pool/ProxyConnection.java:493
   */
  private fun isConnectionClosed(th: SQLException) = th.message == "Connection is closed"

  private fun messageContainsAll(th: SQLException, vararg patterns: String): Boolean {
    val message = th.message ?: return false
    return patterns.all { message.contains(it) }
  }

  private fun messageContainsAny(th: SQLException, vararg patterns: String): Boolean {
    val message = th.message ?: return false
    return patterns.any { message.contains(it) }
  }

  private fun isDatabaseSpecificRetryable(th: SQLException): Boolean {
    return when (dataSourceType) {
      DataSourceType.VITESS_MYSQL -> isVitessRetryable(th)
      DataSourceType.COCKROACHDB -> isCockroachRetryable(th)
      DataSourceType.TIDB -> isTidbRetryable(th)
      else -> false
    }
  }

  /**
   * Vitess-specific retryable exceptions.
   */
  private fun isVitessRetryable(th: SQLException): Boolean {
    return isVitessTransactionNotFound(th)
  }

  /**
   * We get this error as a MySQLQueryInterruptedException when a tablet gracefully terminates,
   * we just need to retry the transaction and the new primary should handle it.
   *
   * ```
   * vttablet: rpc error: code = Aborted desc = transaction 1572922696317821557:
   * not found (CallerID: )
   * ```
   */
  private fun isVitessTransactionNotFound(th: SQLException): Boolean {
    return messageContainsAll(th, "vttablet: rpc error", "code = Aborted", "transaction", "not found")
  }

  /**
   * CockroachDB-specific retryable exceptions.
   *
   * "Messages with the error code 40001 and the string restart transaction indicate that a
   * transaction failed because it conflicted with another concurrent or recent transaction
   * accessing the same data. The transaction needs to be retried by the client."
   * https://www.cockroachlabs.com/docs/stable/common-errors.html#restart-transaction
   */
  private fun isCockroachRetryable(th: SQLException): Boolean {
    return th.errorCode == 40001 && messageContainsAll(th, "restart transaction")
  }

  /**
   * TiDB-specific retryable exceptions.
   *
   * "Transactions in TiKV encounter write conflicts". This can happen when optimistic transaction
   * mode is on. Conflicts are detected during transaction commit
   * https://docs.pingcap.com/tidb/dev/tidb-faq#error-9007-hy000-write-conflict
   */
  private fun isTidbRetryable(th: SQLException): Boolean {
    return th.errorCode == 9007
  }

  protected fun isCauseRetryable(th: Throwable) = th.cause?.let { isRetryable(it) } ?: false
}
