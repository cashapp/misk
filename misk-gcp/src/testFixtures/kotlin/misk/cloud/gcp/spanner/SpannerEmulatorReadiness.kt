package misk.cloud.gcp.spanner

import com.google.cloud.spanner.ErrorCode
import com.google.cloud.spanner.SpannerException
import mu.KotlinLogging

/**
 * Tells a Spanner emulator that is still coming up apart from a real failure, and retries around it.
 *
 * A fresh emulator container answers `listInstances` before it will serve admin writes, so [GoogleSpannerEmulator]
 * needs to wait out an emulator that is up but not ready yet.
 *
 * This is public only so those retries can be tested. Service code has no reason to call it.
 */
object SpannerEmulatorReadiness {
  private val logger = KotlinLogging.logger {}

  /** Codes the emulator returns while it is still coming up. Every other code is a real failure. */
  private val NOT_READY_ERROR_CODES = setOf(ErrorCode.UNAVAILABLE, ErrorCode.DEADLINE_EXCEEDED)

  private const val ATTEMPTS = 10
  private const val BACKOFF_BASE_MILLIS = 100L
  private const val BACKOFF_MAX_MILLIS = 2_000L

  /**
   * Reports whether [failure], or any cause below it, is the emulator refusing a call because it is not ready.
   *
   * The walk down the causes matters: `OperationFuture.get` wraps the Spanner error in an `ExecutionException`, so the
   * failure that reaches a caller is rarely a [SpannerException] itself.
   */
  fun isNotReady(failure: Throwable): Boolean {
    var cause: Throwable? = failure

    while (cause != null) {
      if (cause is SpannerException && cause.errorCode in NOT_READY_ERROR_CODES) return true
      cause = cause.cause
    }

    return false
  }

  /**
   * Runs [block] until it succeeds, and backs off while the emulator is not ready. Any other failure propagates on the
   * first attempt, so a real error surfaces instead of being retried.
   *
   * @param description what the caller is trying to do, for the log line and the give-up message
   * @throws IllegalStateException if the emulator is still not ready after [attempts] tries
   */
  fun <T> retryWhileNotReady(
    description: String,
    attempts: Int = ATTEMPTS,
    backoffBaseMillis: Long = BACKOFF_BASE_MILLIS,
    backoffMaxMillis: Long = BACKOFF_MAX_MILLIS,
    block: () -> T,
  ): T {
    var lastFailure: Throwable? = null
    var backoffMillis = backoffBaseMillis

    repeat(attempts) {
      try {
        return block()
      } catch (e: Throwable) {
        if (!isNotReady(e)) throw e

        lastFailure = e
        logger.info("Spanner emulator cannot $description yet. Retrying in $backoffMillis ms.")
        Thread.sleep(backoffMillis)
        backoffMillis = (backoffMillis * 2).coerceAtMost(backoffMaxMillis)
      }
    }

    throw IllegalStateException("Spanner emulator did not $description in time", lastFailure)
  }
}
