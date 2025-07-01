package misk.lease.mysql

import wisp.lease.Lease
import wisp.lease.LeaseManager
import wisp.logging.getLogger
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi

/**
 * A LeaseManager that uses a SQL database to manage distributed leases.
 */
@ExperimentalMiskApi
class SqlLeaseManager @Inject constructor(
  private val clock: Clock,
  private val config: SqlLeaseConfig,
  private val database: LeaseDatabase,
) : LeaseManager {
  /**
   * Attempts to acquire a lease for a given identifier and duration.
   */
  @Deprecated("Use requestLease(identifier).checkHeld() instead.")
  fun acquire(identifier: String, duration: Duration): Lease? {
    val lease = requestLease(identifier)
    return if (lease.checkHeld()) {
      lease
    } else {
      null
    }
  }

  /**
   * Requests a lease for the given name.
   * If the lease does not exist, tries to insert a new lease row.
   * If the lease is already held, returns a lease object that will fail checkHeld().
   * If the lease is expired, attempts to acquire it by updating the version.
   */
  override fun requestLease(name: String): Lease {
    val lease = database.leaseQueries.selectById(id = name).executeAsOneOrNull()
    val now = clock.instant()
    val heldUntil = now.plus(Duration.ofSeconds(config.leaseDurationInSec))

    if (lease == null) {
      try {
        // Try to insert a new lease row
        database.leaseQueries.insert(
          id = name,
          held_until = heldUntil,
          version = 1L,
        )
        return RealSqlLease(name, 1L)
      } catch (e: SQLException) {
        // If insert fails due to constraint violation (race condition), 
        // return a lease that will fail checkHeld()
        logger.debug(e) { "Failed to insert lease '$name', likely due to race condition" }
        return database.leaseQueries.selectById(id = name).executeAsOne().let {
          RealSqlLease(name, NOT_HELD)
        }
      }
    }

    if (now <= lease.held_until) {
      // Lease is currently held by someone else
      return RealSqlLease(name, NOT_HELD)
    }

    val versionToHold = lease.version + 1L

    // Try to acquire the lease by updating the version and held_until
    val updatedRows = database.leaseQueries.acquire(
      held_until = heldUntil,
      version = versionToHold,
      id = name,
      current_version = lease.version,
    ).value

    if (updatedRows != 1L) {
      // Failed to acquire lease, return a lease that will fail checkHeld()
      return RealSqlLease(name, NOT_HELD)
    }

    return RealSqlLease(name, versionToHold)
  }

  /**
   * Implementation of the Lease interface backed by a SQL row.
   */
  inner class RealSqlLease @JvmOverloads constructor(
    private val identifier: String,
    private val heldVersion: Long,
    override val name: String = identifier,
  ) : Lease {
    private val listeners = mutableListOf<Lease.StateChangeListener>()

    /**
     * Returns true if this process holds the lease (version matches).
     */
    override fun checkHeld(): Boolean {
      val lease = database.leaseQueries.selectById(id = identifier).executeAsOneOrNull()
      return lease?.version == heldVersion
    }

    /**
     * Returns true if the lease is held by another process.
     */
    override fun checkHeldElsewhere(): Boolean = !checkHeld()

    /**
     * Attempts to acquire the lease.
     * Notifies listeners if successful.
     */
    override fun acquire(): Boolean {
      val lease = requestLease(name)
      if (lease.checkHeld()) {
        notifyAfterAcquire()
        return true
      }
      return false
    }

    /**
     * Releases the lease if held.
     * Notifies listeners before releasing.
     */
    override fun release(): Boolean {
      notifyBeforeRelease()
      val deletedRows = database.leaseQueries.release(id = identifier, version = heldVersion).value
      return deletedRows > 0
    }

    /**
     * Registers a listener for lease state changes.
     */
    override fun addListener(listener: Lease.StateChangeListener) {
      listeners.add(listener)
    }

    private fun notifyAfterAcquire() {
      listeners.forEach {
        try {
          it.afterAcquire(this)
        } catch (e: Exception) {
          logger.warn(e) { "exception from afterAcquire() listener on lease $name" }
        }
      }
    }

    private fun notifyBeforeRelease() {
      listeners.forEach {
        try {
          it.beforeRelease(this)
        } catch (e: Exception) {
          logger.warn(e) { "exception from beforeRelease() listener on lease $name" }
        }
      }
    }
  }

  companion object {
    private val logger = getLogger<SqlLeaseManager>()
    
    /** Version number used to indicate a lease is not held by this process */
    const val NOT_HELD = -1L
  }
}
