package wisp.lease.pool

import wisp.lease.Lease

class PoolLease(
  private val delegateLease: Lease,
  private val poolLeaseManager: PoolLeaseManager,
) : Lease {
  override val name: String
    get() = delegateLease.name

  /**
   * Can always use the delegate's result
   */
  override fun checkHeld(): Boolean {
    val held = delegateLease.checkHeld()
    if (!held) {
      poolLeaseManager.clearPoolLeaseMapEntry(name)
    }
    return held
  }

  override fun acquire(): Boolean {
    if (poolLeaseManager.isAcquiredPoolLeaseMapEntry(name)) {
      val acquired = delegateLease.acquire()
      if (!acquired) {
        // we have either lost the lease (or this is a fake deploy or not a pool lease)
        poolLeaseManager.clearPoolLeaseMapEntry(name)
      }
      return acquired
    }

    // if we're not tracking any lease in the pool...
    if (poolLeaseManager.isEmptyPoolLeaseMapEntry(name)) {
      val acquired = delegateLease.acquire()
      if (acquired) {
        // we've acquired the lease, so it's the one to track for the pool
        poolLeaseManager.setPoolLeaseMapEntry(name)
      }
      return acquired
    }
    return false
  }

  override fun release(): Boolean {
    poolLeaseManager.clearPoolLeaseMapEntry(name)
    return true
  }

  override fun addListener(listener: Lease.StateChangeListener) {
    delegateLease.addListener(listener)
  }
}