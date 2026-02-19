package misk.jooq

import java.time.Duration
import misk.backoff.ExponentialBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.jdbc.DataSourceType
import misk.logging.getLogger
import org.jooq.Configuration
import org.jooq.impl.DSL

class JooqTransacter internal constructor(
  private val configurationFactory: (TransacterOptions) -> Configuration,
  private val dataSourceType: DataSourceType? = null,
) {
  private val exceptionClassifier = JooqExceptionClassifier(dataSourceType)

  @JvmOverloads
  fun <RETURN_TYPE> transaction(
    options: TransacterOptions = TransacterOptions(),
    callback: (jooqSession: JooqSession) -> RETURN_TYPE,
  ): RETURN_TYPE {
    val backoff = ExponentialBackoff(
      baseDelay = Duration.ofMillis(options.minRetryDelayMillis),
      maxDelay = Duration.ofMillis(options.maxRetryDelayMillis),
      jitter = Duration.ofMillis(options.retryJitterMillis),
    )
    val retryConfig = RetryConfig.Builder(options.maxAttempts, backoff)
      .shouldRetry { exceptionClassifier.isRetryable(it) }
      .onRetry { attempt, e -> log.info(e) { "JOOQ transaction failed, retrying (attempt $attempt)" } }
      .build()
    return retry(retryConfig) { performInTransaction(options, callback) }
  }

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
    val maxAttempts: Int = 3,
    val minRetryDelayMillis: Long = 100,
    val maxRetryDelayMillis: Long = 500,
    val retryJitterMillis: Long = 400,
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.REPEATABLE_READ,
    val readOnly: Boolean = false,
  )

  companion object {
    private val log = getLogger<JooqTransacter>()

    val noRetriesOptions: TransacterOptions
      get() = TransacterOptions().copy(maxAttempts = 1)
  }
}
