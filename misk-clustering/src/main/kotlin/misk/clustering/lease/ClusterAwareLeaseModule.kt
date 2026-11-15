package misk.clustering.lease

import misk.inject.KAbstractModule
import wisp.lease.LeaseManager

/**
 * Configures a LeaseManager that has leases that always return true for acquire() and isHeld() if the app is running in
 * the active region.
 *
 * This can be used to ignore the lease injection required for Misk SQS Jobs.
 */
class ClusterAwareLeaseModule : KAbstractModule() {
  override fun configure() {
    bind<LeaseManager>().to<ClusterAwareLeaseManager>()
  }
}
