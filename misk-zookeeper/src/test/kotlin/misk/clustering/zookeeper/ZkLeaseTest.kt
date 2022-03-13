package misk.clustering.zookeeper

import com.google.common.collect.Iterables
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.clustering.Cluster
import misk.clustering.fake.FakeCluster
import misk.clustering.weights.FakeClusterWeight
import misk.mockito.Mockito
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.zookeeper.DEFAULT_PERMS
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Id
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import wisp.lease.Lease
import wisp.lease.acquireOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@Suppress("UsePropertyAccessSyntax")
@MiskTest(startService = true)
internal class ZkLeaseTest {
  @MiskTestModule private val module =
    Modules.combine(MiskTestingServiceModule(), ZkLeaseTestModule())

  @Inject lateinit var cluster: FakeCluster
  @Inject lateinit var leaseManager: ZkLeaseManager
  @Inject @ForZkLease lateinit var curator: CuratorFramework
  @Inject lateinit var clusterWeight: FakeClusterWeight
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

    assertThat(connected.await(10, TimeUnit.SECONDS)).isTrue()
  }

  @Test fun acquiresLeaseIfMappedToSelf() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()
    assertThat(lease.checkHeld()).isTrue()

    // Confirm exists in zk
    val leaseData = curator.data.forPath(leasePath.asZkPath)
    assertThat(leaseData.toString(Charsets.UTF_8)).isEqualTo(FakeCluster.SELF_NAME)

    // Data has correct ACL set
    val acl = curator.acl.forPath(leasePath.asZkPath)
    val dnFromCert = "CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US"
    val defaultAcl = ACL(DEFAULT_PERMS, Id("x509", dnFromCert))
    assertThat(Iterables.getOnlyElement(acl)).isEqualTo(defaultAcl)

    // Verify lease directory ACL
    val leasesAcl = curator.acl.forPath("/services/my-app/leases")
    assertThat(Iterables.getOnlyElement(leasesAcl)).isEqualTo(defaultAcl)
  }

  @Test fun doesNotAcquireLeaseIfNotMappedToSelf() {
    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isFalse()

    assertThat(curator.checkExists().forPath(leasePath.asZkPath)).isNull()
  }

  @Test fun releasesLeaseAfterClusterWeightChanges() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    clusterWeight.setClusterWeight(0)
    leaseManager.checkAllLeases()

    assertThat(lease.checkHeld()).isFalse()
  }

  @Test fun releasesAcquiredLeaseIfMappingChangesAwayFromSelf() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    // Fake a cluster change which moves the lease to another process
    cluster.resourceMapper.removeMapping(leasePath)
    leaseManager.checkAllLeases()

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

    curator.close()

    // Should no longer hold the lease
    assertThat(lease.checkHeld()).isFalse()
  }

  @Test fun reacquiresLeaseIfZkDisconnectedWhileLeaseOwned() {
    cluster.resourceMapper.addMapping(leasePath, self)

    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    curator.zookeeperClient.reset()

    // Should no longer hold the lease
    assertThat(lease.checkHeld()).isFalse()

    curator.zookeeperClient.blockUntilConnectedOrTimedOut()

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

  @Test fun listenerNotifications() {
    val listenerMock = Mockito.mock<Lease.StateChangeListener>()

    val lease = leaseManager.requestLease(LEASE_NAME)
    lease.addListener(listenerMock)
    assertThat(lease.checkHeld()).isFalse()
    verifyNoMoreInteractions(listenerMock)

    // Assign the lease to this node
    cluster.resourceMapper.addMapping(leasePath, self)

    verifyNoMoreInteractions(listenerMock)
    // checkHeld() should trigger the lease to be acquired
    assertThat(lease.checkHeld()).isTrue()
    verify(listenerMock, times(1)).afterAcquire(lease)
    reset(listenerMock)

    // Further calls to checkHeld() should not trigger events because the lease does not change
    assertThat(lease.checkHeld()).isTrue()
    assertThat(lease.checkHeld()).isTrue()
    verifyNoMoreInteractions(listenerMock)
    reset(listenerMock)

    // Fake a cluster change which moves the lease to another process
    cluster.resourceMapper.removeMapping(leasePath)

    // Should no longer own the lease and should have deleted the lease node
    assertThat(lease.checkHeld()).isFalse()
    verify(listenerMock, times(1)).beforeRelease(lease)
  }

  @Test fun listenerLateRegistrationGetsNotified() {
    val listenerMock = Mockito.mock<Lease.StateChangeListener>()

    cluster.resourceMapper.addMapping(leasePath, self)
    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    lease.addListener(listenerMock)
    verify(listenerMock, times(1)).afterAcquire(lease)
  }

  @Test fun checkLeaseInListenerDoesNotDeadlock() {
    cluster.resourceMapper.addMapping(leasePath, self)
    val lease = leaseManager.requestLease(LEASE_NAME)
    assertThat(lease.checkHeld()).isTrue()

    val acquireCalled = AtomicBoolean()
    lease.addListener(object : Lease.StateChangeListener {
      override fun afterAcquire(lease: Lease) {
        lease.checkHeld()
        acquireCalled.set(true)
      }

      override fun beforeRelease(lease: Lease) {}
    })

    assertThat(acquireCalled.get()).isTrue()
  }

  @Test fun usableWithAutoCloseable() {
    cluster.resourceMapper.addMapping(leasePath, self)
    val acquireCalled = AtomicBoolean()
    val released = AtomicBoolean()

    val listener = object : Lease.StateChangeListener {
      override fun afterAcquire(lease: Lease) = acquireCalled.set(true)
      override fun beforeRelease(lease: Lease) = released.set(true)
    }
    val log = LinkedBlockingDeque<String>()

    leaseManager.acquireOrNull(LEASE_NAME)?.apply {
      addListener(listener)
    }?.use { lease ->
      log.add("I have the ${lease.name} lease!")
    }

    assertThat(acquireCalled.get()).isTrue()
    assertThat(released.get()).isTrue()
    assertThat(log).containsExactly("I have the $LEASE_NAME lease!")
  }

  companion object {
    private val self = Cluster.Member(name = FakeCluster.SELF_NAME, ipAddress = FakeCluster.SELF_IP)
    private const val LEASE_NAME = "my_lease"
  }
}
