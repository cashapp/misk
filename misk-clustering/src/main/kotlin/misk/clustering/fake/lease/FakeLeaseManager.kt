package misk.clustering.fake.lease

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.FakeFixture
import wisp.lease.Lease
import wisp.lease.LeaseManager
import java.util.concurrent.ConcurrentHashMap

/**
 * A [FakeLeaseManager] provides explicit control over leases for the purposes of testing. By
 * default a lease is considered held, but it can be explicitly marked as not held if desired
 */
@Singleton
class FakeLeaseManager @Inject constructor() : LeaseManager, FakeFixture() {
  private val leasesHeldElsewhere by resettable { ConcurrentHashMap<String, Int>() }
  private val leases by resettable { ConcurrentHashMap<String, FakeLease>() }

  override fun requestLease(name: String): Lease {
    return leases.computeIfAbsent(name) {
      FakeLease(name, this)
    }
  }

  override fun releaseAll() {
    leases.forEachValue(1) {
      it.release()
    }
  }

  fun isLeaseHeldElsewhere(name: String) = leasesHeldElsewhere.containsKey(name)

  fun isLeaseHeld(name: String) = !leasesHeldElsewhere.containsKey(name)

  fun markLeaseHeld(name: String) {
    leasesHeldElsewhere.remove(name)
    (requestLease(name) as FakeLease).acquire()
  }

  fun markLeaseHeldElsewhere(name: String) {
    (requestLease(name) as FakeLease).release()
    leasesHeldElsewhere[name] = 1
  }
}
