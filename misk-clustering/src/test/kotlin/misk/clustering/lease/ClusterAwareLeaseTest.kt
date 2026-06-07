package misk.clustering.lease

import com.google.inject.Module
import com.google.inject.util.Modules
import jakarta.inject.Inject
import java.time.Duration
import misk.MiskTestingServiceModule
import misk.clustering.weights.FakeClusterWeight
import misk.clustering.weights.FakeClusterWeightModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wisp.lease.AcquireOptions
import wisp.lease.LeaseManager
import wisp.lease.WaitMode

@MiskTest
internal class ClusterAwareLeaseTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = Modules.combine(MiskTestingServiceModule(), FakeClusterWeightModule(), ClusterAwareLeaseModule())

  @Inject private lateinit var leaseManager: LeaseManager

  @Inject private lateinit var clusterWeight: FakeClusterWeight

  @Test
  fun testActiveCluster() {
    val lease1 = leaseManager.requestLease("lease1")
    val lease2 = leaseManager.requestLease("lease2")

    assertNotEquals(lease1, lease2)

    // This should work for other lease managers as well.
    assertTrue(lease1.acquire())
    assertTrue(lease1.isHeld())
    assertTrue(lease1.checkHeld())

    // This will be false for any lease managers other than the ClusterAwareLeaseManager
    // because lease hasn't been acquired yet.
    assertTrue(lease2.isHeld())
    assertTrue(lease2.checkHeld())
    assertTrue(lease2.acquire())
  }

  @Test
  fun testPassiveClusterAwareIgnoreLeaseManager() {
    clusterWeight.setClusterWeight(0)
    val lease = leaseManager.requestLease("lease3")

    assertNotNull(lease)

    // Both should be false for ClusterAwareLeaseManager
    assertFalse(lease.acquire())
    assertFalse(lease.isHeld())
    assertFalse(lease.checkHeld())
  }

  @Test
  fun waitOptionsAreNoOpsInActiveCluster() {
    val lease = leaseManager.requestLease("lease4")

    assertTrue(lease.acquire(AcquireOptions(wait = WaitMode.WaitForLeaseDuration)))
    assertTrue(lease.acquire(AcquireOptions(wait = WaitMode.WaitUpTo(Duration.ofSeconds(1)))))
  }

  @Test
  fun waitOptionsAreNoOpsInPassiveCluster() {
    clusterWeight.setClusterWeight(0)
    val lease = leaseManager.requestLease("lease5")

    assertFalse(lease.acquire(AcquireOptions(wait = WaitMode.WaitForLeaseDuration)))
    assertFalse(lease.acquire(AcquireOptions(wait = WaitMode.WaitUpTo(Duration.ofSeconds(1)))))
  }
}
