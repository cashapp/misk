package misk.hibernate

import misk.logging.getLogger
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Times a [session], checking if it has exceeded its [timeouts].
 *
 * An owner [Transacter] should catch any exceptions coming from this and rollback accordingly.
 */
internal class TransactionRunner constructor(
  val executor: ScheduledExecutorService,
  val session: CancellableSession,
  val timeouts: Timeouts
) {
  private val isCancellable = AtomicBoolean(true)
  private val warnFuture: ScheduledFuture<*>? = null
  private val killFuture: ScheduledFuture<*>? = null

  private fun warnCallback() {
    logger.warn {
      "Running transaction has exceeded ${timeouts.warnAfter.toMillis()} ms. " +
          "Performance may be degraded?"
    }
  }

  private fun killCallback() {
    // Cancel the session so that the next operation will throw and rollback the transaction.
    session.hibernateSession.cancelQuery()
    if (isCancellable.get()) {
      session.cancel()
      isCancellable.set(false)
      logger.warn {
        "Cancelled session for this transaction because it took too long " +
            "(${timeouts.killAfter.toMillis()} ms)"
      }
    }
  }

  /**
   * Runs the body of a transaction with the [session] owned by this runner.
   *
   * When the [timeouts] warn after duration is exceeded, a warning will be logged.
   * When the [timeouts] kill after duration is exceeded, the [session] will be cancelled, and the
   * transaction will be rolled back.
   */
  fun <T> doWork(work: (Session) -> T): T {
    return doWork(work, ::warnCallback, ::killCallback)
  }

  // Exposed for testing.
  internal fun <T> doWork(
    work: (Session) -> T,
    warnCallback: () -> Unit,
    killCallback: () -> Unit
  ): T {
    check(warnFuture == null && killFuture == null) {
      "A new runner is required for each transaction!"
    }

    val warnFuture = if (timeouts.warnTimeoutEnabled()) {
      executor.schedule(warnCallback, timeouts.warnAfter.toMillis(), TimeUnit.MILLISECONDS)
    } else {
      null
    }
    val killFuture = if (timeouts.killTimeoutEnabled()) {
      executor.schedule(killCallback, timeouts.killAfter.toMillis(), TimeUnit.MILLISECONDS)
    } else {
      null
    }

    try {
      val result = work(session)
      isCancellable.set(false)
      return result
    } finally {
      warnFuture?.cancel(false)
      killFuture?.cancel(false)
    }
  }

  internal class Factory @Inject constructor(
    @ForHibernate private val executor: ScheduledExecutorService
  ) {
    fun create(session: CancellableSession, timeouts: Timeouts) = TransactionRunner(
        executor = executor,
        session = session,
        timeouts = timeouts
    )
  }

  companion object {
    val logger = getLogger<TransactionRunner>()
  }
}