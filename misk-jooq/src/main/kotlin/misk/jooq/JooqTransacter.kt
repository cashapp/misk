package misk.jooq

import java.time.Duration
import misk.backoff.ExponentialBackoff
import misk.logging.getLogger
import org.jooq.Configuration
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL

class JooqTransacter internal constructor(private val configurationFactory: (TransacterOptions) -> Configuration) {

  @JvmOverloads
  fun <RETURN_TYPE> transaction(
    options: TransacterOptions = TransacterOptions(),
    callback: (jooqSession: JooqSession) -> RETURN_TYPE,
  ): RETURN_TYPE {
    val backoff = ExponentialBackoff(Duration.ofMillis(10L), Duration.ofMillis(options.maxRetryDelayMillis))
    var attempt = 0

    while (true) {
      try {
        return performInTransaction(options, callback, ++attempt)
      } catch (e: Exception) {
        if (e !is DataAccessException || attempt >= options.maxAttempts) throw e
        val sleepDuration = backoff.nextRetry()
        if (!sleepDuration.isZero) {
          Thread.sleep(sleepDuration.toMillis())
        }
      }
    }
  }

  private fun <RETURN_TYPE> performInTransaction(
    options: TransacterOptions,
    callback: (jooqSession: JooqSession) -> RETURN_TYPE,
    attempt: Int,
  ): RETURN_TYPE {
    return try {
      val result = createDSLContextAndCallback(options, callback)
      if (attempt > 1) {
        log.info { "Retried jooq transaction succeeded after [attempts=$attempt]" }
      }
      result
    } catch (e: Exception) {
      if (attempt >= options.maxAttempts) {
        log.warn(e) { "Recoverable transaction exception [attempts=$attempt], no more attempts" }
        throw e
      }
      log.info(e) { "Exception thrown while transacting with the db via jooq" }
      throw e
    }
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
            .getOrElse { throw it } // JooqExtensions.kt shadows Result<T>.getOrThrow()... This is equivalent.
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
    val maxRetryDelayMillis: Long = 500,
    val isolationLevel: TransactionIsolationLevel = TransactionIsolationLevel.REPEATABLE_READ,
  )

  companion object {
    private val log = getLogger<JooqTransacter>()

    val noRetriesOptions: TransacterOptions
      get() = TransacterOptions().copy(maxAttempts = 1)
  }
}
