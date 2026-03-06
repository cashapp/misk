package misk.cron

import jakarta.inject.Inject
import java.time.Duration
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retry
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.inject.FakeSwitch
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.tasks.DelayedTask
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestFixture
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class CronAsyncSwitchTest {
  @Suppress("unused")
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        install(CronTestingModule())
        install(CronEntryModule.create<MinuteCron>())

        bind<FakeSwitch>().asSingleton()
        bindOptionalBinding<misk.inject.AsyncSwitch>().to<FakeSwitch>()
        multibind<TestFixture>().to<FakeSwitch>()
      }
    }

  @Inject private lateinit var cronManager: CronManager
  @Inject private lateinit var clock: FakeClock
  @Inject private lateinit var fakeSwitch: FakeSwitch
  @Inject private lateinit var minuteCron: MinuteCron
  @Inject lateinit var pendingTasks: ExplicitReleaseDelayQueue<DelayedTask>

  @BeforeEach
  fun setUp() {
    fakeSwitch.enabledKeys.add("cron")
  }

  @Test
  fun `crons are skipped when async switch is disabled`() {
    clock.add(Duration.ofMinutes(1))
    waitForNextPendingTask().task()
    cronManager.waitForCronsComplete()
    assertThat(minuteCron.counter).isEqualTo(1)

    fakeSwitch.enabledKeys.remove("cron")

    clock.add(Duration.ofMinutes(1))
    waitForNextPendingTask().task()
    cronManager.waitForCronsComplete()
    assertThat(minuteCron.counter).isEqualTo(1)

    fakeSwitch.enabledKeys.add("cron")

    clock.add(Duration.ofMinutes(1))
    waitForNextPendingTask().task()
    cronManager.waitForCronsComplete()
    assertThat(minuteCron.counter).isEqualTo(2)
  }

  private fun waitForNextPendingTask(): DelayedTask =
    retry(RetryConfig.Builder(5, FlatBackoff(Duration.ofMillis(200))).build()) { pendingTasks.peekPending()!! }
}
