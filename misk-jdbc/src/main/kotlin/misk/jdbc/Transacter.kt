package misk.jdbc

import java.sql.Connection

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
   * Returns a new transacter configured to retry transactions up to [maxAttempts] times on retryable exceptions.
   * Default is 3 attempts.
   */
  fun retries(maxAttempts: Int): Transacter

  /**
   * Returns a new transacter configured to not retry transactions.
   */
  fun noRetries(): Transacter
}

class RealTransacter private constructor(
  private val dataSourceService: DataSourceService,
  private val config: DataSourceConfig?,
  private val options: TransacterOptions,
) : Transacter {
  private val transacting = ThreadLocal.withInitial { false }
  private val exceptionClassifier = misk.jdbc.retry.DefaultExceptionClassifier(config?.type)

  constructor(dataSourceService: DataSourceService) : this(
    dataSourceService = dataSourceService,
    config = null,
    options = TransacterOptions(),
  )

  constructor(dataSourceService: DataSourceService, config: DataSourceConfig) : this(
    dataSourceService = dataSourceService,
    config = config,
    options = TransacterOptions(),
  )

  override val inTransaction: Boolean
    get() = transacting.get()

  @Deprecated("Use transactionWithSession instead", replaceWith = ReplaceWith("transactionWithSession(work)"))
  override fun <T> transaction(work: (connection: Connection) -> T): T = transactionWithSession { session ->
    session.useConnection(work)
  }

  override fun <T> transactionWithSession(work: (session: JDBCSession) -> T): T {
    return transactionWithRetries { transactionInternal(work) }
  }

  private fun <T> transactionWithRetries(block: () -> T): T {
    val backoff = misk.backoff.ExponentialBackoff(
      baseDelay = java.time.Duration.ofMillis(options.minRetryDelayMillis),
      maxDelay = java.time.Duration.ofMillis(options.maxRetryDelayMillis),
      jitter = java.time.Duration.ofMillis(options.retryJitterMillis)
    )
    val retryConfig = misk.backoff.RetryConfig.Builder(options.maxAttempts, backoff)
      .shouldRetry { exceptionClassifier.isRetryable(it) }
      .onRetry { attempt, e -> logger.info(e) { "JDBC transaction failed, retrying (attempt $attempt)" } }
      .build()
    return misk.backoff.retry(retryConfig) { block() }
  }

  private fun <T> transactionInternal(work: (session: JDBCSession) -> T): T {
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
      }
    } finally {
      transacting.set(false)
      session?.executeSessionCloseHooks()
    }
  }

  override fun retries(maxAttempts: Int): Transacter = RealTransacter(
    dataSourceService = dataSourceService,
    config = config,
    options = options.copy(maxAttempts = maxAttempts),
  )

  override fun noRetries(): Transacter = retries(1)

  internal data class TransacterOptions(
    val maxAttempts: Int = 3,
    val minRetryDelayMillis: Long = 100,
    val maxRetryDelayMillis: Long = 500,
    val retryJitterMillis: Long = 400,
  )
}

private val logger = misk.logging.getLogger<RealTransacter>()
