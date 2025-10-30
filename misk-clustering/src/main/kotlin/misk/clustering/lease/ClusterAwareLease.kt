package misk.clustering.lease

import misk.clustering.weights.ClusterWeightProvider
import wisp.lease.Lease

/**
 * Provides functions to acquire and check if a lease is held.
 * Returns true for acquire() and checkHeld() if the app is running in the active region.
 *
 * This lease serves as a no-op lease, suitable for situations where lease injection is necessary
 * but not functionally important, such as in Misk SQS.
 */
class ClusterAwareLease(
  override val name: String,
  private val clusterWeightProvider: ClusterWeightProvider
) : Lease {
  override fun acquire(): Boolean {
    return (clusterWeightProvider.get() != 0)
  }

  override fun addListener(listener: Lease.StateChangeListener) {
  }

  override fun checkHeld(): Boolean {
    return (clusterWeightProvider.get() != 0)
  }

  override fun checkHeldElsewhere(): Boolean {
    return (clusterWeightProvider.get() == 0)
  }

  override fun release(): Boolean {
    return true
  }

  override fun release(lazy: Boolean): Boolean {
    return true
  }

  override fun shouldHold(): Boolean {
    return (clusterWeightProvider.get() != 0)
  }
}
