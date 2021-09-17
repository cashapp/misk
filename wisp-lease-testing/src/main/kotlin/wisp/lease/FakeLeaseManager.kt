package wisp.lease

import java.util.concurrent.ConcurrentHashMap

/**
 * A [FakeLeaseManager] provides explicit control over leases for the purposes of testing. By
 * default, a fake lease is considered held, but it can be explicitly marked as not held if desired
 */
open class FakeLeaseManager : LeaseManager {
  private val leasesHeldElsewhere = ConcurrentHashMap<String, Int>()
  private val leases = ConcurrentHashMap<String, FakeLease>()

  override fun requestLease(name: String): Lease {
    return leases.computeIfAbsent(name) {
      FakeLease(name, this)
    }
  }

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
