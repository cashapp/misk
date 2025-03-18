package misk.vitess

import com.zaxxer.hikari.SQLExceptionOverride
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import wisp.logging.getLogger

internal class VitessExceptionHandler(
  registry: CollectorRegistry? = null,
) : SQLExceptionOverride {
  private val logger = getLogger<VitessExceptionHandler>()

  private val errorCounter = registry?.let { registry ->
    Counter
    .build("hikaricp_misk_vitess_exception", "Misk Vitess Exception that were recorded for the hikari pool")
    .labelNames("errorCode", "sqlState", "message", "decision")
      .create()
    .registerOrReplace(registry)
  }

  private val probablyBadState =
    setOf(
    // VT05005: typical of a connection error if we can't find the keyspace. Abort the connection
    KnownErrors(sqlState = "42S02", errorCode = 1146),
    KnownErrors(sqlState = "hy000", errorCode = 1105)
  )

  private val badErrorString = listOf(
    Regex("connection"),
    Regex("vttable")
  )

  private data class KnownErrors(
    val sqlState: String,
    val errorCode: Int,
  )

  private fun SQLException.toKnownErrors(): KnownErrors =
    KnownErrors(
      sqlState = this.sqlState ?: "",
      errorCode = this.errorCode
    )

  private fun containsKnownBadErrorMessage(message: String?): Boolean {
    if (message == null) return false;
    return badErrorString.find { it.containsMatchIn(message) } != null
  }

  private fun adjudicateInternal(sqlException: SQLException): SQLExceptionOverride.Override {
    return when {
      sqlException.toKnownErrors() in probablyBadState -> SQLExceptionOverride.Override.MUST_EVICT
      containsKnownBadErrorMessage(sqlException.message) -> SQLExceptionOverride.Override.MUST_EVICT
      else -> SQLExceptionOverride.Override.CONTINUE_EVICT
    }
  }

  override fun adjudicate(sqlException: SQLException): SQLExceptionOverride.Override {
    return adjudicateInternal(sqlException).also { result ->
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
        sqlException.sqlState,
        sqlException.message,
        result.toString()
      )?.inc()
    }
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
  }
}
