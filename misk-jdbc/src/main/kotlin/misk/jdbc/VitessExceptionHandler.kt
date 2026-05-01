package misk.jdbc

import com.zaxxer.hikari.SQLExceptionOverride
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import misk.logging.getLogger

internal class VitessExceptionHandler(registry: CollectorRegistry? = null) : SQLExceptionOverride {
  private val logger = getLogger<VitessExceptionHandler>()

  private val errorCounter =
    registry?.let { registry ->
      Counter.build("hikaricp_misk_vitess_exception", "Misk Vitess Exception that were recorded for the hikari pool")
        .labelNames("errorCode", "sqlState", "message", "decision")
        .create()
        .registerOrReplace(registry)
    }

  private val probablyBadState =
    setOf(
      // VT05005: typical of a connection error if we can't find the keyspace. Abort the connection
      KnownErrors(sqlState = "42S02", errorCode = 1146), // VT05005: keyspace not found
      KnownErrors(sqlState = "HY000", errorCode = 1105), // general Vitess internal error
      KnownErrors(sqlState = "HY000", errorCode = 2013), // Lost connection to server during query
      KnownErrors(sqlState = "HY000", errorCode = 2006), // MySQL server has gone away
      KnownErrors(sqlState = "08S01", errorCode = 0), // Communication link failure
      KnownErrors(sqlState = "08003", errorCode = 0), // Connection does not exist
      KnownErrors(sqlState = "08006", errorCode = 0), // Connection failure
    )

  // States that are definitely NOT connection problems — keep the connection
  private val probablySafeState =
    setOf(
      KnownErrors(sqlState = "23000", errorCode = 1062), // Duplicate entry
      KnownErrors(sqlState = "23000", errorCode = 1452), // FK constraint fails
      KnownErrors(sqlState = "42000", errorCode = 1064), // Syntax error
      KnownErrors(sqlState = "22001", errorCode = 1406), // Data too long
      KnownErrors(sqlState = "21S01", errorCode = 1136), // Column count mismatch
    )

  // TODO(young): These are a bit of a band-aid, ideally we catch these with their associated error codes, but I'm
  // scared of just removing them until we've measured them in the wild, particularly the vtgate one.
  private val badErrorString =
    listOf(
      Regex("vttablet:.*connection.*refused", RegexOption.IGNORE_CASE),
      Regex("vttablet:.*connection.*reset", RegexOption.IGNORE_CASE),
      Regex("vttablet:.*broken pipe", RegexOption.IGNORE_CASE),
      Regex("vttablet:.*connection.*closed", RegexOption.IGNORE_CASE),
      Regex("vtgate:.*connection error", RegexOption.IGNORE_CASE),
    )

  private data class KnownErrors(val sqlState: String, val errorCode: Int)

  private fun SQLException.toKnownErrors(): KnownErrors =
    KnownErrors(sqlState = this.sqlState ?: "", errorCode = this.errorCode)

  private fun containsKnownBadErrorMessage(message: String?): Boolean {
    if (message == null) return false
    return badErrorString.find { it.containsMatchIn(message) } != null
  }

  private fun adjudicateInternal(sqlException: SQLException): SQLExceptionOverride.Override {
    return when {
      sqlException.toKnownErrors() in probablyBadState -> SQLExceptionOverride.Override.MUST_EVICT
      sqlException.toKnownErrors() in probablySafeState -> SQLExceptionOverride.Override.DO_NOT_EVICT
      containsKnownBadErrorMessage(sqlException.message) -> SQLExceptionOverride.Override.MUST_EVICT
      sqlException.sqlState?.uppercase()?.startsWith("HY") == true -> SQLExceptionOverride.Override.MUST_EVICT
      sqlException.sqlState?.startsWith("21") == true -> SQLExceptionOverride.Override.DO_NOT_EVICT
      sqlException.sqlState?.startsWith("22") == true -> SQLExceptionOverride.Override.DO_NOT_EVICT
      sqlException.sqlState?.startsWith("23") == true -> SQLExceptionOverride.Override.DO_NOT_EVICT
      sqlException.sqlState?.startsWith("42") == true -> SQLExceptionOverride.Override.CONTINUE_EVICT
      sqlException.sqlState?.startsWith("08") == true -> SQLExceptionOverride.Override.MUST_EVICT
      else -> SQLExceptionOverride.Override.CONTINUE_EVICT
    }
  }

  override fun adjudicate(sqlException: SQLException): SQLExceptionOverride.Override {
    return adjudicateInternal(sqlException).also { result ->
      val message =
        "Hikari exception adjudicate: " +
          "errorCode=${sqlException.errorCode}, " +
          "state=${sqlException?.sqlState ?: "null"}, " +
          "message=${sqlException.message}, " +
          "adjudicate=$result"
      if (result == SQLExceptionOverride.Override.DO_NOT_EVICT) {
        logger.warn(message)
      } else {
        logger.error(message)
      }
      errorCounter
        ?.labels(sqlException.errorCode.toString(), sqlException.sqlState, sqlException.message, result.toString())
        ?.inc()
    }
  }

  companion object {
    /**
     * This uses a similar check that Hikari does internally. We can't register the same counter twice. Instead we use
     * some global memory to find the existing reference and return it instead.
     */
    private val registrationStatuses: ConcurrentHashMap<CollectorRegistry, Counter> = ConcurrentHashMap()

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
