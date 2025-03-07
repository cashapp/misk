package misk.vitess

import com.zaxxer.hikari.SQLExceptionOverride
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import java.lang.ref.WeakReference
import java.sql.SQLException
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal class VitessExceptionHandler(
  registry: CollectorRegistry? = null,
) : SQLExceptionOverride {

  private val errorCounter = registry?.let { registry ->
    Counter
    .build("hikaricp_misk_vitess_exception", "Misk Vitess Exception that were recorded for the hikari pool")
    .labelNames("errorCode", "sqlState", "message", "decision")
      .create()
    .registerOrReplace(registry)
  }

  private val probablyBadState = setOf(
    // VT05005: typical of a connection error if we can't find the keyspace. Abort the connection
    KnownErrors(sqlState = "42S02", errorCode = 1146)
  )

  private val badErrorString = listOf(
    Regex("connection")
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
     * This uses a similar that Hikari does internally. We can't register the same counter twice.
     * instead we use some global memory to find the existing reference and return it instead.
     */
    private val registrationStatuses: ConcurrentHashMap<CollectorRegistry, Counter> =
      ConcurrentHashMap()
    private fun Counter.registerOrReplace(registry: CollectorRegistry): Counter {
      val existingCounter = registrationStatuses.putIfAbsent(registry, this)
      return if (existingCounter == null) {
        registry.register(this)
        this
      } else {
       existingCounter
      }
    }
  }
}
