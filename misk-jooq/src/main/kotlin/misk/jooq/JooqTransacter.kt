package misk.jooq

import java.time.Duration
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.jdbc.DataSourceType
import misk.jdbc.retry.RetryDefaults
import misk.logging.getLogger
import org.jooq.Configuration
import org.jooq.impl.DSL

class JooqTransacter
internal constructor(
  private val configurationFactory: (TransacterOptions) -> Configuration,
  private val dataSourceType: DataSourceType? = null,
) {
  private val exceptionClassifier = JooqExceptionClassifier(dataSourceType)

  @JvmOverloads
  fun <RETURN_TYPE> transaction(
    options: TransacterOptions = TransacterOptions(),
    callback: (jooqSession: JooqSession) -> RETURN_TYPE,
  ): RETURN_TYPE {
    val backoff =
      ExponentialBackoff(
        baseDelay = Duration.ofMillis(options.minRetryDelayMillis),
        maxDelay = Duration.ofMillis(options.maxRetryDelayMillis),
        jitter = Duration.ofMillis(options.retryJitterMillis),
      )
    val retryConfig =
      RetryConfig.Builder(options.maxAttempts, backoff)
        .shouldRetry { exceptionClassifier.isRetryable(it) }
        .onRetry { attempt, e -> log.info(e) { "jOOQ transaction failed, retrying (attempt $attempt)" } }
        .build()
    return retry(retryConfig) { performInTransaction(options, callback) }
  }

  /**
   * Runs [block] inside [ambient] if supplied; otherwise opens a new transaction and passes its [JooqSession] to
   * [block].
   *
   * Designed for repository methods that may run standalone or be called from within an outer transaction. Callers that
   * already hold a session pass it as [ambient] so the work participates in the outer commit boundary instead of
   * opening a nested transaction.
   *
   * ```
   * fun create(name: String, ambient: JooqSession? = null) =
   *   transacter.transactionOrAmbient(ambient) { session -> ... }
   *
   * transacter.transactionOrAmbient { outer -> repo.create("foo", ambient = outer) }
   * ```
   *
   * When [ambient] is non-null no new transaction is opened and retry/backoff is skipped — the outer transaction owns
   * those concerns. When [ambient] is null this delegates to [transaction] with default options.
   *
   * Coroutine-safe by construction: the active session is passed explicitly through the call chain rather than tracked
   * in thread-local state, so suspension and dispatcher hops cannot desynchronize the lookup from the underlying JDBC
   * connection.
   */
  @JvmOverloads
  fun <RETURN_TYPE> transactionOrAmbient(
    ambient: JooqSession? = null,
    block: (JooqSession) -> RETURN_TYPE,
  ): RETURN_TYPE = if (ambient != null) block(ambient) else transaction(callback = block)

  private fun <RETURN_TYPE> performInTransaction(
    options: TransacterOptions,
    callback: (jooqSession: JooqSession) -> RETURN_TYPE,
  ): RETURN_TYPE {
    return createDSLContextAndCallback(options, callback)
  }

  private fun <RETURN_TYPE> createDSLContextAndCallback(
    options: TransacterOptions,
    callback: (jooqSession: JooqSession) -> RETURN_TYPE,
  ): RETURN_TYPE {
    var jooqSession: JooqSession? = null
    return try {
      DSL.using(configurationFactory(options))
        .transactionResult { configuration ->
          jooqSession = JooqSession(DSL.using(configuration))
          runCatching { callback(jooqSession).also { jooqSession.executePreCommitHooks() } }
            .onFailure { jooqSession.onSessionClose { jooqSession.executeRollbackHooks(it) } }
            .getOrThrow()
        }
        .also { jooqSession?.executePostCommitHooks() }
    } finally {
      jooqSession?.executeSessionCloseHooks()
    }
  }

  data class TransacterOptions
  @JvmOverloads
  constructor(
    val maxAttempts: Int = RetryDefaults.MAX_ATTEMPTS,
    val minRetryDelayMillis: Long = RetryDefaults.MIN_RETRY_DELAY_MILLIS,
    val maxRetryDelayMillis: Long = RetryDefaults.MAX_RETRY_DELAY_MILLIS,
    val retryJitterMillis: Long = RetryDefaults.RETRY_JITTER_MILLIS,
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.REPEATABLE_READ,
    val readOnly: Boolean = false,
  )

  companion object {
    private val log = getLogger<JooqTransacter>()

    val noRetriesOptions: TransacterOptions
      get() = TransacterOptions().copy(maxAttempts = 1)
  }
}
