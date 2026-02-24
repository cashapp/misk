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
    var retried = false
    val retryConfig = RetryConfig.Builder(options.maxAttempts, backoff)
      .shouldRetry { exceptionClassifier.isRetryable(it) }
      .onRetry { attempt, e ->
        retried = true
        log.info(e) { "jOOQ transaction failed, retrying (attempt $attempt)" }
      }
      .build()
    return retry(retryConfig) { performInTransaction(options, callback) }.also {
      if (retried) {
        log.info { "Retried jooq transaction succeeded" }
      }
    }
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
