package misk.hibernate

import com.google.common.annotations.VisibleForTesting

/**
 * Provides explicit block-based transaction demarcation.
 */
interface Transacter {
  /**
   * Returns true if the calling thread is currently within a transaction block.
   */
  val inTransaction: Boolean

  /**
   * Is the scalability check currently enabled. Use [Session.withoutChecks] to disable checks.
   */
  fun isCheckEnabled(check: Check): Boolean

  /**
   * Starts a transaction on the current thread, executes lambda, and commits the transaction.
   * If lambda raises an exception the transaction will be rolled back instead of committed.
   *
   * If retries are permitted (the default), a failed but recoverable transaction will be
   * reattempted after rolling back.
   *
   * It is an error to start a transaction if another transaction is already in progress.
   */
  fun <T> transaction(lambda: (session: Session) -> T): T

  fun retries(maxAttempts: Int = 2): Transacter

  fun noRetries(): Transacter

  /**
   * Creates a new transacter that produces read only sessions. This does not mean the underlying
   * datasource is read only, only that the session produced won't modify the database.
   */
  fun readOnly(): Transacter

  /**
   * Disable cowrite checks for the duration of the session. Useful for quickly setting up test
   * data in testing.
   */
  // TODO(jontirsen): Figure out a way to make this only available for test code
  @VisibleForTesting
  fun allowCowrites(): Transacter
}

fun Transacter.shards() = transaction { it.shards() }

fun Transacter.shards(keyspace: Keyspace) = transaction { it.shards(keyspace) }

fun <T> Transacter.transaction(shard: Shard, lambda: (session: Session) -> T): T =
    transaction { it.target(shard) { lambda(it) } }

/**
 * Thrown to explicitly trigger a retry, subject to retry limits and config such as noRetries().
 */
class RetryTransactionException(
  message: String? = null,
  cause: Throwable? = null
) : Exception(message, cause)
