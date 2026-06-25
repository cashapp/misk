package misk.jdbc

import java.sql.Connection
import java.time.Duration
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.jdbc.retry.DefaultExceptionClassifier
import misk.jdbc.retry.RetryDefaults
import misk.logging.getLogger

interface Transacter {
  /** Returns true if the calling thread is currently within a transaction block. */
  val inTransaction: Boolean

  /**
   * Starts a transaction on the current thread, executes [work], and commits the transaction. If the work raises an
   * exception the transaction will be rolled back instead of committed.
   *
   * It is an error to start a transaction if another transaction is already in progress.
   *
   * Prefer using [transactionWithSession] instead of this method as it has more functionality such as commit hooks.
   */
  @Deprecated("Use transactionWithSession instead", replaceWith = ReplaceWith("transactionWithSession(work)"))
  fun <T> transaction(work: (connection: Connection) -> T): T

  /**
   * Starts a transaction on the current thread, executes [work], and commits the transaction. If the work raises an
   * exception the transaction will be rolled back instead of committed.
   *
   * This session object passed in wraps a connection and provides a way to add pre and post commit hooks that execute
   * before and after a transaction is committed.
   *
   * It is an error to start a transaction if another transaction is already in progress.
   */
  fun <T> transactionWithSession(work: (session: JDBCSession) -> T): T

  /**
   * Like [transactionWithSession], but applies the per-transaction [options] to this single transaction only.
   *
   * The default implementation ignores [options] and behaves identically to [transactionWithSession]; implementations
   * that support per-transaction options (such as [RealTransacter]) override it. See [TransactionOptions].
   */
  fun <T> transactionWithSession(options: TransactionOptions, work: (session: JDBCSession) -> T): T =
    transactionWithSession(work)

  /**
   * Returns a new transacter configured to retry transactions up to [maxAttempts] times on retryable exceptions.
   * Default is 3 attempts.
   */
  fun retries(maxAttempts: Int): Transacter

  /** Returns a new transacter configured to not retry transactions. */
  fun noRetries(): Transacter
}

/**
 * Options that apply to a single [Transacter.transactionWithSession] call.
 *
 * Unlike [Transacter.retries]/[Transacter.noRetries], which return a reconfigured transacter, these options are scoped
 * to the one transaction they are passed to and never affect other transactions sharing the pool.
 */
data class TransactionOptions
@JvmOverloads
constructor(
  /**
   * Isolation level to apply to this transaction only.
   *
   * It is set on the connection after it is acquired and before the transaction begins. Misk begins transactions lazily
   * — the database transaction starts on the first statement — so the level takes effect before the transaction begins,
   * which matters on MySQL where `setTransactionIsolation` issues `SET SESSION ...` and is a no-op for an
   * already-started transaction. The connection's previous level is restored before it is returned to the pool, so the
   * change never leaks to the next borrower, regardless of whether the pool configures
   * [DataSourceConfig.transaction_isolation].
   *
   * When `null` (the default) the connection's isolation is left unchanged.
   */
  val isolationLevel: TransactionIsolationLevel? = null
)

class RealTransacter
private constructor(
  private val dataSourceService: DataSourceService,
  private val config: DataSourceConfig?,
  private val options: TransacterOptions,
) : Transacter {
  private val transacting = ThreadLocal.withInitial { false }
  private val exceptionClassifier = DefaultExceptionClassifier(config?.type)

  constructor(
    dataSourceService: DataSourceService
  ) : this(dataSourceService = dataSourceService, config = null, options = TransacterOptions())

  constructor(
    dataSourceService: DataSourceService,
    config: DataSourceConfig,
  ) : this(dataSourceService = dataSourceService, config = config, options = TransacterOptions())

  override val inTransaction: Boolean
    get() = transacting.get()

  @Deprecated("Use transactionWithSession instead", replaceWith = ReplaceWith("transactionWithSession(work)"))
  override fun <T> transaction(work: (connection: Connection) -> T): T = transactionWithSession { session ->
    session.useConnection(work)
  }

  override fun <T> transactionWithSession(work: (session: JDBCSession) -> T): T =
    transactionWithSession(TransactionOptions(), work)

  override fun <T> transactionWithSession(options: TransactionOptions, work: (session: JDBCSession) -> T): T {
    return transactionWithRetries { transactionInternal(options, work) }
  }

  private fun <T> transactionWithRetries(block: () -> T): T {
    val backoff =
      ExponentialBackoff(
        baseDelay = Duration.ofMillis(options.minRetryDelayMillis),
        maxDelay = Duration.ofMillis(options.maxRetryDelayMillis),
        jitter = Duration.ofMillis(options.retryJitterMillis),
      )
    val retryConfig =
      RetryConfig.Builder(options.maxAttempts, backoff)
        .shouldRetry { exceptionClassifier.isRetryable(it) }
        .onRetry { attempt, e -> logger.info(e) { "JDBC transaction failed, retrying (attempt $attempt)" } }
        .build()
    return retry(retryConfig) { block() }
  }

  private fun <T> transactionInternal(options: TransactionOptions, work: (session: JDBCSession) -> T): T {
    check(!transacting.get()) { "The current thread is already in a transaction" }
    transacting.set(true)

    var session: JDBCSession? = null
    try {
      return dataSourceService.dataSource.connection.use { connection ->
        /*
         * We are using Hikari, which will automatically roll back incomplete transactions.
         * This means there's actually no need to wrap the transaction in a try clause to
         * do rollback on exception
         */

        // Apply a caller-requested isolation level for this transaction only. Misk begins transactions lazily (the
        // database transaction starts on the first statement), so setting it here — after acquiring the connection and
        // before any work runs — takes effect before the transaction begins. We capture the connection's prior level
        // and restore it once the transaction completes so the change never leaks to the next borrower of this pooled
        // connection, independent of whether the pool configures transaction_isolation (Hikari's reset-on-return only
        // fires when it is).
        val isolationToRestore: Int? =
          options.isolationLevel?.let { requested ->
            connection.transactionIsolation.also { connection.transactionIsolation = requested.jdbcLevel }
          }

        try {
          // BEGIN
          if (connection.autoCommit) {
            connection.autoCommit = false
          }

          // Do stuff
          session = JDBCSession(connection)
          val result =
            runCatching { work(session) }
              .onFailure { e -> session.onSessionClose { session.executeRollbackHooks(e) } }
              .getOrThrow()

          // COMMIT
          session.executePreCommitHooks()
          connection.commit()
          session.executePostCommitHooks()
          result
        } finally {
          isolationToRestore?.let { connection.transactionIsolation = it }
        }
      }
    } finally {
      transacting.set(false)
      session?.executeSessionCloseHooks()
    }
  }

  override fun retries(maxAttempts: Int): Transacter =
    RealTransacter(
      dataSourceService = dataSourceService,
      config = config,
      options = options.copy(maxAttempts = maxAttempts),
    )

  override fun noRetries(): Transacter = retries(1)

  internal data class TransacterOptions(
    val maxAttempts: Int = RetryDefaults.MAX_ATTEMPTS,
    val minRetryDelayMillis: Long = RetryDefaults.MIN_RETRY_DELAY_MILLIS,
    val maxRetryDelayMillis: Long = RetryDefaults.MAX_RETRY_DELAY_MILLIS,
    val retryJitterMillis: Long = RetryDefaults.RETRY_JITTER_MILLIS,
  )

  companion object {
    private val logger = getLogger<RealTransacter>()
  }
}
