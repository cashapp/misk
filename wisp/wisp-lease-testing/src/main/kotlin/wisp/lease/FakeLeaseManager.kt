package wisp.lease

import java.util.concurrent.ConcurrentHashMap
import misk.testing.FakeFixture

/**
 * A [FakeLeaseManager] provides explicit control over leases for the purposes of testing. By default, a fake lease is
 * considered held, but it can be explicitly marked as not held if desired
 */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "FakeLeaseManager()", imports = ["misk.clustering.fake.lease"]),
)
open class FakeLeaseManager : LeaseManager, FakeFixture() {
  private val leasesHeldElsewhere by resettable { ConcurrentHashMap<String, Int>() }
  private val leases by resettable { ConcurrentHashMap<String, FakeLease>() }

  override fun requestLease(name: String): Lease {
    return leases.computeIfAbsent(name) { FakeLease(name, this) }
  }

  override fun releaseAll() {
    leases.forEachValue(1) { it.release() }
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
