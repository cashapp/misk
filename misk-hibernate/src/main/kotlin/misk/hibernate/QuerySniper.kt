package misk.hibernate

import misk.logging.getLogger
import org.hibernate.BaseSessionEventListener
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Hooks on the start and end of query executions. Queries that take too long will be logged or
 * cancelled. Time limits are configured by the [misk.jdbc.DataSourceConfig].
 *
 * In a transaction, wrap a query with [Session.runSlowQuery] to run it with higher time limits.
 *
 * ```
 * transacter.transaction { session -> {
 *   session.runSlowQuery { superSlowQuery(session) }
 * }
 * ```
 */
internal class QuerySniper constructor(
  val executor: ScheduledExecutorService,
  val onWarn: (() -> Unit)? = null,
  val onKill: (() -> Unit)? = null
) {
  /** Watch a [session] so that slow queries may be killed if they exceed its timeouts. */
  fun watch(session: Session) {
    session.hibernateSession.addEventListeners(object : BaseSessionEventListener() {
      private var warnFuture: ScheduledFuture<*>? = null
      private var killFuture: ScheduledFuture<*>? = null
      private val timeouts get() = session.timeouts()

      private val warnCallback: () -> Unit = onWarn ?: this::onWarn
      private val killCallback: () -> Unit = onKill ?: this::onKill

      private fun onWarn() {
        logger.warn {
          "Query has exceeded ${timeouts.warnAfter.toMillis()} ms. " + if (killFuture != null) {
            "Will kill if it exceeds ${timeouts.killAfter.toMillis()} ms."
          } else {
            "Won't kill."
          }
        }
      }

      private fun onKill() {
        session.hibernateSession.cancelQuery()
        logger.warn { "Query exceeded ${timeouts.killAfter.toMillis()} ms. Killed it!" }
      }

      override fun jdbcExecuteStatementStart() {
        check(warnFuture == null && killFuture == null) { "Re-entrant JDBC statement!" }

        warnFuture = if (timeouts.warnTimeoutEnabled()) {
          executor.schedule(warnCallback, timeouts.warnAfter.toMillis(), TimeUnit.MILLISECONDS)
        } else {
          null
        }

        killFuture = if (timeouts.killTimeoutEnabled()) {
          executor.schedule(killCallback, timeouts.killAfter.toMillis(), TimeUnit.MILLISECONDS)
        } else {
          null
        }
      }

      override fun jdbcExecuteStatementEnd() {
        warnFuture?.cancel(false)
        warnFuture = null
        killFuture?.cancel(false)
        killFuture = null
      }
    })
  }

  internal class Factory @Inject constructor(
    @ForHibernate private val executor: ScheduledExecutorService
  ) {
    fun create() = QuerySniper(executor)
    fun create(onWarn: (() -> Unit)?, onKill: (() -> Unit)?) = QuerySniper(executor, onWarn, onKill)
  }

  companion object {
    val logger = getLogger<QuerySniper>()
  }
}