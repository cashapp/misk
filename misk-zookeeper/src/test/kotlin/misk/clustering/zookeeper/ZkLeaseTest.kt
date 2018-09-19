package misk.clustering.zookeeper

import com.google.inject.util.Modules
import misk.MiskServiceModule
import misk.clustering.fake.FakeCluster
import misk.clustering.leasing.LeaseManager
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class ZkLeaseTest {
  @MiskTestModule private val module = Modules.combine(MiskServiceModule(), ZkTestModule())

  @Inject lateinit var cluster: FakeCluster
  @Inject lateinit var leaseManager: LeaseManager

  @Test fun acquiresLeaseIfMappedToSelf() {

  }

  @Test fun doesNotAcquireLeaseIfNotMappedToSelf() {
  }

  @Test fun releasedAcquiredLeasedIfNotMappedToSelf() {
  }

  @Test fun doesNotAcquireLeaseIfServiceNotRunning() {
  }

  @Test fun doesNotAcquireLeaseIfZkDisconnected() {
  }

  @Test fun failsAcquireLeaseIfOwnedByAnotherProcess() {
  }

  @Test fun attemptsToReacquireLeaseIfZkDisconnectedWhileLeaseOwned() {
  }

  @Test fun releaseLeasesAtShutdown() {
  }

  @Test fun doesNotReleaseLeaseIfNotExists() {
  }

  @Test fun doesNotReleaseLeaseIfHeldByAnotherProcess() {
  }

  @Test fun ignoresNodeNotExistsErrorsDuringRelease() {
  }

}