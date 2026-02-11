package misk.jdbc.retry

import misk.jdbc.DataSourceType
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException

/**
 * Default exception classifier that handles common database retry scenarios.
 */
open class DefaultExceptionClassifier @JvmOverloads constructor(
  private val databaseType: DataSourceType? = null
) : ExceptionClassifier {

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
      (databaseType == DataSourceType.VITESS_MYSQL && isRecoverableVitessException(th)) ||
      (databaseType == DataSourceType.COCKROACHDB && isRecoverableCockroachException(th)) ||
      (databaseType == DataSourceType.TIDB && isRecoverableTidbException(th))

  /**
   * This is thrown as a raw SQLException from Hikari even though it is most certainly a
   * recoverable exception. See com/zaxxer/hikari/pool/ProxyConnection.java:493
   */
  protected fun isConnectionClosed(th: SQLException): Boolean =
    th.message.equals("Connection is closed")

  /**
   * Vitess-specific recoverable exceptions.
   */
  protected fun isRecoverableVitessException(th: SQLException): Boolean {
    return isVitessTransactionNotFound(th)
  }

  /**
   * CockroachDB-specific recoverable exceptions.
   */
  protected fun isRecoverableCockroachException(th: SQLException): Boolean {
    return isCockroachRestartTransaction(th)
  }

  /**
   * TiDB-specific recoverable exceptions.
   */
  protected fun isRecoverableTidbException(th: SQLException): Boolean {
    return isTidbWriteConflict(th)
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
    val message = th.message
    return message != null &&
      message.contains("vttablet: rpc error") &&
      message.contains("code = Aborted") &&
      message.contains("transaction") &&
      message.contains("not found")
  }

  /**
   * "Messages with the error code 40001 and the string restart transaction indicate that a
   * transaction failed because it conflicted with another concurrent or recent transaction
   * accessing the same data. The transaction needs to be retried by the client."
   * https://www.cockroachlabs.com/docs/stable/common-errors.html#restart-transaction
   */
  private fun isCockroachRestartTransaction(th: SQLException): Boolean {
    val message = th.message
    return th.errorCode == 40001 && message != null && message.contains("restart transaction")
  }

  /**
   * "Transactions in TiKV encounter write conflicts". This can happen when optimistic
   * transaction mode is on. Conflicts are detected during transaction commit.
   * https://docs.pingcap.com/tidb/dev/tidb-faq#error-9007-hy000-write-conflict
   */
  private fun isTidbWriteConflict(th: SQLException): Boolean {
    return th.errorCode == 9007
  }

  protected fun isCauseRetryable(th: Throwable): Boolean =
    th.cause?.let { isRetryable(it) } ?: false
}
