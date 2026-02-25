package misk.jooq.transacter

import java.time.Duration
import misk.jooq.JooqSession
import misk.jooq.TransactionIsolationLevel

/**
 * Thread-safe transacter for jOOQ database operations with a fluent configuration API.
 *
 * Example:
 * ```kotlin
 * transacter.readOnly().maxRetries(3).transaction { session ->
 *   session.ctx.selectFrom(MY_TABLE).fetch()
 * }
 * ```
 */
interface Transacter {
  /** Returns `true` if the calling thread is currently within a transaction block. */
  val inTransaction: Boolean

  /**
   * Executes [closure] within a transaction, committing on success or rolling back on failure.
   *
   * @throws IllegalStateException If a transaction is already in progress on the current thread.
   */
  fun <T> transaction(closure: (session: JooqSession) -> T): T

  /** Configures the next transaction to be read-only. */
  fun readOnly(): Transacter

  /** Configures the next transaction to allow writes (the default). */
  fun writable(): Transacter

  /**
   * Executes [closure] as a read-only transaction against the replica data source.
   *
   * @throws IllegalStateException If no reader data source is configured.
   * @throws IllegalStateException If a transaction is already in progress on the current thread.
   */
  fun <T> replicaRead(closure: (session: JooqSession) -> T): T

  /** Configures the maximum number of retries for transient failures. Total executions = maxRetries + 1. */
  fun maxRetries(maxRetries: Int): Transacter

  /** Disables retries for the next transaction (equivalent to `maxRetries(0)`). */
  fun noRetries(): Transacter

  /** Configures the maximum delay between retry attempts. */
  fun maxRetryDelay(duration: Duration): Transacter

  /** Configures the transaction isolation level. */
  fun isolationLevel(level: TransactionIsolationLevel): Transacter
}
