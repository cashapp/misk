package misk.jdbc

import com.google.common.annotations.VisibleForTesting
import com.zaxxer.hikari.SQLExceptionOverride
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import misk.logging.getLogger
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * No functional exception handling overrides, but logs an exception and emits error metrics.
 */
internal class MySQLExceptionHandler(
  registry: CollectorRegistry? = null,
): SQLExceptionOverride {

  override fun adjudicate(sqlException: SQLException): SQLExceptionOverride.Override {
    val result = super.adjudicate(sqlException)
    logger.error(
      "Hikari exception adjudicate: " +
        "errorCode=${sqlException.errorCode}, " +
        // the logic around sqlState kotlin this is null safe, but the Java shows it being set
        // as null. Let's be explicit at the behavir.
        "state=${sqlException?.sqlState ?: "null"}, " +
        "message=${sqlException.message}, " +
        "adjudicate=$result"
    )
    errorCounter?.labels(
      sqlException.errorCode.toString(),
      sqlException.sqlState ?: "",
      sqlException.message,
      result.name
    )?.inc()
    return result
  }

  @VisibleForTesting
  val errorCounter = registry?.let { registry ->
    Counter
      .build("hikaricp_misk_mysql_exception", "Misk MySQL Exceptions that were recorded for the hikari pool")
      .labelNames("errorCode", "sqlState", "message", "decision")
      .create()
      .registerOrReplace(registry)
  }

  companion object {
    /**
     * This uses a similar check that Hikari does internally. We can't register the same counter twice.
     * Instead we use some global memory to find the existing reference and return it instead.
     */
    private val registrationStatuses: ConcurrentHashMap<CollectorRegistry, Counter> =
      ConcurrentHashMap()
    private fun Counter.registerOrReplace(registry: CollectorRegistry): Counter {
      val existingCounter = registrationStatuses.putIfAbsent(registry, this)
      return if (existingCounter == null) {
        registry.register(this)
        // make sure we have some baseline labels
        this.labels("", "", "", "CONTINUE_EVICT")
        this
      } else {
        existingCounter
      }
    }

    private val logger = getLogger<MySQLExceptionHandler>()
  }
}
