package misk.jooq

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.StopRetrying
import com.github.michaelbull.retry.context.retryStatus
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.fullJitterBackoff
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import wisp.logging.getLogger
import kotlin.coroutines.coroutineContext

class JooqTransacter(
  private val dslContext: DSLContext
) {

  fun <RETURN_TYPE> transaction(
    options: TransacterOptions = TransacterOptions(),
    callback: (jooqSession: JooqSession) -> RETURN_TYPE
  ): RETURN_TYPE {
    return runBlocking {
      retry(
        retryJooqExceptionsAlone +
          limitAttempts(options.maxAttempts) +
          fullJitterBackoff(base = 10L, max = options.maxRetryDelayMillis)
      ) {
        performInTransaction(options, callback)
      }
    }
  }

  private suspend fun <RETURN_TYPE> performInTransaction(
    options: TransacterOptions,
    callback: (jooqSession: JooqSession) -> RETURN_TYPE
  ): RETURN_TYPE {
    val attempt1Based = coroutineContext.retryStatus.attempt + 1
    return try {
      val result = createDSLContextAndCallback(callback)
      if (attempt1Based > 1) {
        log.info {
          "Retried jooq transaction succeeded after [attempts=$attempt1Based]"
        }
      }
      result
    } catch (e: Exception) {
      if (attempt1Based >= options.maxAttempts) {
        log.warn(e) {
          "Recoverable transaction exception [attempts=$attempt1Based], no more attempts"
        }
        throw e
      }
      log.info(e) { "Exception thrown while transacting with the db via jooq" }
      throw e
    }
  }

  private fun <RETURN_TYPE> createDSLContextAndCallback(
    callback: (jooqSession: JooqSession) -> RETURN_TYPE
  ): RETURN_TYPE {
    var jooqSession: JooqSession? = null
    return try {
      dslContext.transactionResult { configuration ->
        jooqSession = JooqSession(DSL.using(configuration))
        jooqSession to callback(jooqSession!!)
      }.let { (jooqSession, callbackResult) ->
        jooqSession!!.executePostCommitHooks()
        callbackResult
      }
    }finally {
      jooqSession?.executeSessionCloseHooks()
    }
  }

  data class TransacterOptions(
    val maxAttempts: Int = 3,
    val maxRetryDelayMillis: Long = 500,
  )

  companion object {
    val log = getLogger<JooqTransacter>()
    private val retryJooqExceptionsAlone: RetryPolicy<Throwable> = {
      if (reason is DataAccessException) ContinueRetrying else StopRetrying
    }
    val noRetriesOptions: TransacterOptions
      get() = TransacterOptions().copy(maxAttempts = 1)
  }
}
