package misk.lease.mysql

import wisp.lease.Lease
import wisp.lease.LeaseManager
import wisp.lease.PersistentLeaseManager
import misk.logging.getLogger
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import jakarta.inject.Inject
import misk.annotation.ExperimentalMiskApi

/**
 * A LeaseManager that uses a SQL database to manage distributed leases.
 */
@ExperimentalMiskApi
internal class SqlLeaseManager @Inject constructor(
  private val clock: Clock,
  private val database: LeaseDatabase,
) : PersistentLeaseManager {

  /**
   * Requests a lease for the given name .
   */
  override fun requestLease(name: String): Lease {
    return requestLease(name, DEFAULT_LEASE_DURATION)
  }

  /**
   * Requests a lease for the given name with an explicit duration.
   */
  fun requestLease(name: String, duration: Duration): Lease {
    val lease = database.leaseQueries.selectByLeaseName(lease_name = name).executeAsOneOrNull()
    val now = clock.instant()
    val heldUntil = now.plus(duration)

    return when {
      lease == null -> {
        // Lease doesn't exist, try to create it
        try {
          database.leaseQueries.insert(
            lease_name = name,
            version = 1L,
            held_until = heldUntil,
          )
          RealSqlLease(name, 1L, heldUntil)
        } catch (e: SQLException) {
          logger.debug(e) { "Failed to insert lease '$name', likely due to race condition" }
          RealSqlLease(name, NOT_HELD, Instant.EPOCH)
        }
      }
      
      now <= lease.held_until -> {
        // Lease is currently held by someone else
        RealSqlLease(name, NOT_HELD, Instant.EPOCH)
      }
      
      else -> {
        // Lease exists but is expired, try to acquire it
        val newVersion = lease.version + 1L
        val updatedRows = database.leaseQueries.acquire(
          held_until = heldUntil,
          version = newVersion,
          lease_name = name,
          current_version = lease.version,
        ).value

        if (updatedRows == 1L) {
          // Successfully acquired the lease
          RealSqlLease(name, newVersion, heldUntil)
        } else {
          // Someone else acquired it first (race condition)
          RealSqlLease(name, NOT_HELD, Instant.EPOCH)
        }
      }
    }
  }

  /**
   * Implementation of the Lease interface backed by a SQL row.
   */
  inner class RealSqlLease constructor(
    override val name: String,
    private val heldVersion: Long,
    private val heldUntil: Instant,
  ) : Lease {
    private val listeners = mutableListOf<Lease.StateChangeListener>()

    override fun shouldHold(): Boolean = true

    /**
     * Returns true if this process holds the lease.
     */
    override fun checkHeld(): Boolean {
      if (heldVersion == NOT_HELD) return false
      
      // We hold the lease until it expires
      return clock.instant() <= heldUntil
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
      if (heldVersion == NOT_HELD) return false
      
      notifyBeforeRelease()
      
      val deletedRows = database.leaseQueries.release(
        lease_name = name,
        version = heldVersion
      ).value
      return deletedRows > 0
    }

    override fun release(lazy: Boolean): Boolean = release()

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
    
    /** Default lease duration (5 minutes) */
    val DEFAULT_LEASE_DURATION: Duration = Duration.ofSeconds(300)
    
    /** Version number used to indicate a lease is not held by this process */
    const val NOT_HELD = -1L
  }
}
