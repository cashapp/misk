package misk.clustering.fake.lease

import com.google.common.collect.Sets.newConcurrentHashSet
import misk.clustering.lease.Lease
import misk.clustering.lease.LeaseManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [FakeLeaseManager] provides explicit control over leases for the purposes of testing. By
 * default a lease is considered held, but it can be explicitly marked as not held if desired
 */
@Singleton
class FakeLeaseManager @Inject constructor() : LeaseManager {
  private val leasesHeldElsewhere = newConcurrentHashSet<String>()
  private val leases = ConcurrentHashMap<String, FakeLease>()

  override fun requestLease(name: String): Lease {
    return leases.computeIfAbsent(name) {
      FakeLease(name, this)
    }
  }

  fun isLeaseHeld(name: String) = !leasesHeldElsewhere.contains(name)

  fun markLeaseHeld(name: String) {
    leasesHeldElsewhere.remove(name)
    (requestLease(name) as FakeLease).notifyAfterAcquire()
  }

  fun markLeaseHeldElsewhere(name: String) {
    (requestLease(name) as FakeLease).notifyBeforeRelease()
    leasesHeldElsewhere.add(name)
  }

  internal class FakeLease(
    override val name: String,
    private val manager: FakeLeaseManager
  ) : Lease {
    private val listeners = mutableListOf<Lease.StateChangeListener>()

    override fun checkHeld() = manager.isLeaseHeld(name)

    override fun addListener(listener: Lease.StateChangeListener) {
      listeners.add(listener)
      if (checkHeld()) {
        listener.afterAcquire(this)
      }
    }

    fun notifyAfterAcquire() {
      listeners.forEach {
        it.afterAcquire(this)
      }
    }

    fun notifyBeforeRelease() {
      listeners.forEach {
        it.beforeRelease(this)
      }
    }
  }
}
