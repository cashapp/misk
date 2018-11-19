package misk.clustering.fake.lease

import com.google.common.collect.Sets.newConcurrentHashSet
import misk.clustering.lease.Lease
import misk.clustering.lease.LeaseManager
import javax.inject.Singleton

/**
 * A [FakeLeaseManager] provides explicit control over leases for the purposes of testing. By
 * default a lease is considered held, but it can be explicitly marked as not held if desired
 */
@Singleton
class FakeLeaseManager : LeaseManager {
  private val leasesHeldElsewhere = newConcurrentHashSet<String>()

  override fun requestLease(name: String): Lease = FakeLease(name, this)

  fun isLeaseHeld(name: String) = !leasesHeldElsewhere.contains(name)

  fun markLeaseHeld(name: String) {
    leasesHeldElsewhere.remove(name)
  }

  fun markLeaseHeldElsewhere(name: String) {
    leasesHeldElsewhere.add(name)
  }

  internal class FakeLease(
    override val name: String,
    private val manager: FakeLeaseManager
  ) : Lease {
    override fun checkHeld() = manager.isLeaseHeld(name)
  }
}