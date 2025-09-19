package wisp.lease.pool

import wisp.lease.Lease

class PoolLease(
  private val delegateLease: Lease,
  private val poolLeaseManager: PoolLeaseManager,
) : Lease by delegateLease {

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

  override fun checkHeldElsewhere(): Boolean {
    if (checkHeld()) return false

    return delegateLease.checkHeldElsewhere()
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
    val released = delegateLease.release()
    if (released) {
      poolLeaseManager.clearPoolLeaseMapEntry(name)
    }
    return released
  }

  override fun release(lazy: Boolean): Boolean {
    val released = delegateLease.release(lazy = lazy)
    if (released) {
      poolLeaseManager.clearPoolLeaseMapEntry(name)
    }
    return released
  }
}
