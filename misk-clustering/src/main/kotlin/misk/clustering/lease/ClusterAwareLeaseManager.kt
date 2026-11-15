package misk.clustering.lease

import jakarta.inject.Inject
import misk.clustering.weights.ClusterWeightProvider
import wisp.lease.LeaderLeaseManager
import wisp.lease.Lease
import wisp.lease.LoadBalancedLeaseManager

/** Returns a Lease that always returns true for acquire() and isHeld() if the app is running in the active region. */
class ClusterAwareLeaseManager @Inject internal constructor(private val clusterWeightProvider: ClusterWeightProvider) :
  LeaderLeaseManager, LoadBalancedLeaseManager {
  override fun requestLease(name: String): Lease {
    return ClusterAwareLease(name, clusterWeightProvider)
  }
}
