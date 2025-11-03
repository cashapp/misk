package misk.cron

import misk.clustering.fake.lease.FakeLeaseManager
import misk.inject.KAbstractModule
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import misk.cron.SingleLeaseCronCoordinator.Companion.CRON_CLUSTER_LEASE_NAME
import org.junit.jupiter.api.BeforeEach

class CronCoordinatorTest {
  @Suppress("unused")
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(CronTestingModule())
    }
  }

  private lateinit var fakeLeaseManager: FakeLeaseManager

  @BeforeEach
  fun setUp() {
    fakeLeaseManager = FakeLeaseManager()
  }

  @Test
  fun singleLeaseCoordinatorUsesGlobalLease() {
    val coordinator = SingleLeaseCronCoordinator(fakeLeaseManager)

    fakeLeaseManager.markLeaseHeld(CRON_CLUSTER_LEASE_NAME)
    assertThat(coordinator.shouldRunTask("task1")).isTrue()
    assertThat(coordinator.shouldRunTask("task2")).isTrue()

    fakeLeaseManager.markLeaseHeldElsewhere(CRON_CLUSTER_LEASE_NAME)
    assertThat(coordinator.shouldRunTask("task1")).isFalse()
    assertThat(coordinator.shouldRunTask("task2")).isFalse()
  }

  @Test
  fun distributedCoordinatorUsesTaskSpecificLeases() {
    val coordinator = MultipleLeaseCronCoordinator(fakeLeaseManager)

    fakeLeaseManager.markLeaseHeld("misk.cron.task.task1")
    fakeLeaseManager.markLeaseHeldElsewhere("misk.cron.task.task2")

    assertThat(coordinator.shouldRunTask("task1")).isTrue()
    assertThat(coordinator.shouldRunTask("task2")).isFalse()

    fakeLeaseManager.markLeaseHeldElsewhere("misk.cron.task.task1")
    fakeLeaseManager.markLeaseHeld("misk.cron.task.task2")

    assertThat(coordinator.shouldRunTask("task1")).isFalse()
    assertThat(coordinator.shouldRunTask("task2")).isTrue()
  }

  @Test
  fun singleLeaseCoordinatorAcquiresLeaseWhenNotHeld() {
    val coordinator = SingleLeaseCronCoordinator(fakeLeaseManager)

    // Mark lease as held elsewhere first, then verify acquisition
    fakeLeaseManager.markLeaseHeldElsewhere(CRON_CLUSTER_LEASE_NAME)
    assertThat(coordinator.shouldRunTask("task1")).isFalse()

    // Now mark as available and verify it gets acquired
    fakeLeaseManager.markLeaseHeld(CRON_CLUSTER_LEASE_NAME)
    assertThat(coordinator.shouldRunTask("task1")).isTrue()
  }

  @Test
  fun distributedCoordinatorAcquiresTaskLeaseWhenNotHeld() {
    val coordinator = MultipleLeaseCronCoordinator(fakeLeaseManager)

    // Mark task lease as held elsewhere first
    fakeLeaseManager.markLeaseHeldElsewhere("misk.cron.task.task1")
    assertThat(coordinator.shouldRunTask("task1")).isFalse()

    // Now mark as available and verify it gets acquired
    fakeLeaseManager.markLeaseHeld("misk.cron.task.task1")
    assertThat(coordinator.shouldRunTask("task1")).isTrue()
  }
}