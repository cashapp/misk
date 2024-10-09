package misk.clustering.lease

import jakarta.inject.Inject
import misk.clustering.weights.ClusterWeightProvider
import wisp.lease.Lease
import wisp.lease.LeaseManager

/**
 * Returns a Lease that always returns true for acquire() and
 * checkHeld() if the app is running in the active region.
 */
class ClusterAwareLeaseManager @Inject internal constructor(
  private val clusterWeightProvider: ClusterWeightProvider
) : LeaseManager {
  override fun requestLease(name: String): Lease {
    return ClusterAwareLease(name, clusterWeightProvider)
  }
}
