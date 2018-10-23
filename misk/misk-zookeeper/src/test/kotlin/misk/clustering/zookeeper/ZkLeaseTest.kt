package misk.clustering.zookeeper

import com.google.inject.util.Modules
import misk.MiskServiceModule
import misk.clustering.Cluster
import misk.clustering.fake.FakeCluster
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.ZooDefs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class ZkLeaseTest {
  @MiskTestModule private val module = Modules.combine(MiskServiceModule(), ZkTestModule())

  @Inject lateinit var cluster: FakeCluster
  @Inject lateinit var leaseManager: ZkLeaseManager
  @Inject lateinit var curator: CuratorFramework
  lateinit var leaseNamespace: String
  lateinit var leasePath: String

  @BeforeEach
  fun initCurator() {
    // By default, map all resources to another process
    cluster.resourceMapper.setDefaultMapping(Cluster.Member("not-me", "10.0.0.3"))

    leaseNamespace = leaseManager.leaseNamespace
    leasePath = "$leaseNamespace/$LEASE_NAME"

    // Wait for the lease manager to become connected to Zk
    val connected = CountDownLatch(1)
    leaseManager.addConnectionListener {
      if (it && connected.count > 0) connected.countDown()
    }

    assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue()
  }

  @Test fun acquiresLeaseIfMappedToSelf() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()
    assertThat(lease.checkHeld()).isTrue()

    // Confirm exists in zk
    val leaseData = curator.data.forPath(leasePath.asZkPath)
    assertThat(leaseData.toString(Charsets.UTF_8)).isEqualTo(FakeCluster.SELF_NAME)
  }

  @Test fun doesNotAcquireLeaseIfNotMappedToSelf() {
    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isFalse()

    assertThat(curator.checkExists().forPath(leasePath.asZkPath)).isNull()
  }

  @Test fun releasesAcquiredLeaseIfMappingChangesAwayFromSelf() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    // Fake a cluster change which moves the lease to another process
    cluster.resourceMapper.removeMapping(leasePath)
    leaseManager.handleClusterChange()

    // Should no longer own the lease and should have deleted the lease node
    assertThat(lease.checkHeld()).isFalse()
    assertThat(curator.checkExists().forPath(leasePath.asZkPath)).isNull()
  }

  @Test fun doesNotAcquireLeaseIfManagerShutdown() {
    cluster.resourceMapper.addMapping(leasePath, self)

    leaseManager.stopAsync()
    leaseManager.awaitTerminated(5, TimeUnit.SECONDS)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isFalse()

    // Should never have created the node
    assertThat(curator.checkExists().forPath(leasePath.asZkPath)).isNull()
  }

  @Test fun failsAcquireLeaseIfOwnedByAnotherProcess() {
    cluster.resourceMapper.addMapping(leasePath, self)

    curator.usingNamespace(leaseNamespace.asZkNamespace)
        .create()
        .withMode(CreateMode.EPHEMERAL)
        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
        .forPath(LEASE_NAME.asZkPath, "some other process".toByteArray())

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isFalse()
  }

  @Test fun doesNotHoldLeaseIfZkDisconnected() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    // Fake a disconnect from zk
    leaseManager.handleConnectionStateChanged(false)

    // Should no longer hold the lease
    assertThat(lease.checkHeld()).isFalse()
  }

  @Test fun reacquiresLeaseIfZkDisconnectedWhileLeaseOwned() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    // Fake a disconnect from zk
    leaseManager.handleConnectionStateChanged(false)

    // Should no longer hold the lease
    assertThat(lease.checkHeld()).isFalse()

    // Reconnect to zk
    leaseManager.handleConnectionStateChanged(true)

    // Should reacquire the lease
    assertThat(lease.checkHeld()).isTrue()
  }

  @Test fun releaseLeasesAtShutdown() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    lease.close()
    assertThat(lease.checkHeld()).isFalse()
    assertThat(curator.checkExists().forPath(leasePath.asZkPath)).isNull()
  }

  @Test fun doesNotReleaseLeaseIfNotHeld() {
    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isFalse()

    // Fake create the node and have its contents match what we expect. This ensures we're
    // testing a different path from when another process owns the node
    curator.create()
        .withMode(CreateMode.EPHEMERAL)
        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
        .forPath(leasePath.asZkPath, FakeCluster.SELF_IP.toByteArray())

    // Since we don't own the lease, we won't attempt to delete the node
    lease.close()
    assertThat(lease.checkHeld()).isFalse()
    assertThat(curator.checkExists().forPath(leasePath.asZkPath)).isNotNull()
  }

  @Test fun doesNotReleaseLeaseIfHeldByAnotherProcess() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    // Change the contents of the node so it is owned by another process
    curator.delete().forPath(leasePath.asZkPath)
    curator.create()
        .withMode(CreateMode.EPHEMERAL)
        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
        .forPath(leasePath.asZkPath, "some other process".toByteArray())

    // Even though we own the lease, we won't attempt to delete the node since it's
    // owned by someone else
    lease.close()
    assertThat(lease.checkHeld()).isFalse()
    assertThat(curator.checkExists().forPath(leasePath.asZkPath)).isNotNull()
  }

  @Test fun neverExitsClosedState() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    lease.close()

    // Fake a disconnect and reconnect - should still not exit from closed
    leaseManager.handleConnectionStateChanged(false)
    leaseManager.handleConnectionStateChanged(true)

    assertThat(lease.checkHeld()).isFalse() // still not held since its closed
  }

  companion object {
    private val self = Cluster.Member(name = FakeCluster.SELF_NAME, ipAddress = FakeCluster.SELF_IP)
    private const val LEASE_NAME = "my_lease"

  }
}