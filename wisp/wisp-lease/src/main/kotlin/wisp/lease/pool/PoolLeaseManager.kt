package wisp.lease.pool

import wisp.deployment.Deployment
import wisp.lease.Lease
import wisp.lease.LeaseManager

/**
 * A [LeaseManager] that ensures that leases that are in the same lease pool are acquired on
 * different instances.  It requires a delegate [LeaseManager] to handle the [Lease] implementation.
 *
 * This lease acquisition restriction is applied when [Deployment.isReal] is true.
 */
class PoolLeaseManager(
  private val delegateLeaseManager: LeaseManager,
  private val deployment: Deployment,
  poolConfig: List<PoolLeaseConfig>,
) : LeaseManager {

  /**
   * Keep track of the leases that have been requested
   */
  private val leases = mutableMapOf<String, PoolLease>()

  /**
   * Map of the lease names to their pool name for look up.
   */
  private val leaseToPoolNameMap = mutableMapOf<String, String>()

  /**
   * Track the lease acquired for a pool
   */
  private val poolLeaseAcquiredMap = mutableMapOf<String, String>()

  init {
    poolConfig.forEach { config ->
      config.leaseNames.forEach { leaseName ->
        leaseToPoolNameMap[leaseName] = config.name
      }
    }
  }

  /**
   * Removes this lease as the acquired lease if it's in the map
   */
  internal fun clearPoolLeaseMapEntry(leaseName: String) {
    leaseToPoolNameMap[leaseName]?.let { poolName ->
      if (poolLeaseAcquiredMap[poolName] == leaseName) {
        poolLeaseAcquiredMap.remove(poolName)
      }
    }
  }

  /**
   * Set this lease as the acquired lease for the pool it's in (if in a pool)
   */
  internal fun setPoolLeaseMapEntry(leaseName: String) {
    leaseToPoolNameMap[leaseName]?.let { poolName ->
      poolLeaseAcquiredMap[poolName] = leaseName
    }
  }

  /**
   * Returns true if we're not tracking any lease for the pool
   */
  internal fun isEmptyPoolLeaseMapEntry(leaseName: String): Boolean {
    leaseToPoolNameMap[leaseName]?.let { poolName ->
      return poolLeaseAcquiredMap[poolName] == null
    }
    return true
  }

  /**
   * Returns true if the lease is the acquired lease in the pool (if in a pool)
   * OR it's a fake deployment (i.e. all leases are allowed)
   */
  internal fun isAcquiredPoolLeaseMapEntry(leaseName: String): Boolean {
    leaseToPoolNameMap[leaseName]?.let { poolName ->
      return (deployment.isFake || poolLeaseAcquiredMap[poolName] == leaseName)
    }
    return true
  }

  override fun requestLease(name: String): Lease {
    val poolLease = leases.getOrPut(name) {
      PoolLease(delegateLeaseManager.requestLease(name), this)
    }
    return poolLease
  }
}

