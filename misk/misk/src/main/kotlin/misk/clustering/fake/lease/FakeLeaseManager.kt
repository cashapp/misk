package misk.clustering.fake.lease

import com.google.common.collect.Sets.newConcurrentHashSet
import misk.clustering.lease.Lease
import misk.clustering.lease.LeaseManager
import javax.inject.Singleton

/** A [FakeLeaseManager] provides explicit control over leases for the purposes of testing */
@Singleton
class FakeLeaseManager : LeaseManager {
  private val heldLeases = newConcurrentHashSet<String>()

  override fun requestLease(name: String): Lease = FakeLease(name, this)

  fun isLeaseHeld(name: String) = heldLeases.contains(name)

  fun markLeaseHeld(name: String) {
    heldLeases.add(name)
  }

  fun markLeaseReleased(name: String) {
    heldLeases.remove(name)
  }

  internal class FakeLease(
    override val name: String,
    private val manager: FakeLeaseManager
  ) : Lease {
    override fun checkHeld() = manager.isLeaseHeld(name)
  }
}