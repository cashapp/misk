package misk.cron

import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.clustering.weights.FakeClusterWeight
import misk.concurrent.ExplicitReleaseDelayQueue
import misk.inject.KAbstractModule
import misk.tasks.DelayedTask
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import misk.time.FakeClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.backoff.RetryConfig

@MiskTest(startService = true)
class CronTest {
  @Suppress("unused")
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(CronTestingModule())

      install(CronEntryModule.create<MinuteCron>())
      install(CronEntryModule.create<HourCron>())
      install(CronEntryModule.create<ThrowsExceptionCron>())
      // Override ConfigurableHourCron's schedule to every minute.
      install(CronEntryModule.create<ConfigurableHourCron>(cronPattern = CronPattern("* * * * *")))
    }
  }

  @Inject private lateinit var cronManager: CronManager
  @Inject private lateinit var clock: FakeClock
  @Inject lateinit var pendingTasks: ExplicitReleaseDelayQueue<DelayedTask>
  @Inject lateinit var fakeClusterWeight: FakeClusterWeight

  @Inject private lateinit var minuteCron: MinuteCron
  @Inject private lateinit var hourCron: HourCron
  @Inject private lateinit var throwsExceptionCron: ThrowsExceptionCron
  @Inject private lateinit var configurableHourCron: ConfigurableHourCron

  private lateinit var lastRun: Instant

  // This simulates what the automated part of cron does.
  private fun runCrons() {
    val now = clock.instant()
    cronManager.runReadyCrons(lastRun)
    lastRun = now
    cronManager.waitForCronsComplete()
  }

  @Test
  fun basic() {
    lastRun = clock.instant()

    assertThat(minuteCron.counter).isEqualTo(0)
    assertThat(hourCron.counter).isEqualTo(0)
    assertThat(throwsExceptionCron.counter).isEqualTo(0)
    assertThat(configurableHourCron.counter).isEqualTo(0)

    // Advance one hour in one minute intervals.
    repeat(60) {
      clock.add(Duration.ofMinutes(1))
      runCrons()
    }

    assertThat(minuteCron.counter).isEqualTo(60)
    assertThat(throwsExceptionCron.counter).isEqualTo(4)
    assertThat(hourCron.counter).isEqualTo(1)
    // configurableHourCron has @CronPattern configured to hourly, but is overridden
    // to every minute by CronEntryModule::create().
    assertThat(configurableHourCron.counter).isEqualTo(60)
  }

  @Test
  fun leaseDenied() {
    assertThat(minuteCron.counter).isEqualTo(0)
    // Cluster weight is 100 by default, so the cron will run.
    clock.add(Duration.ofMinutes(1))
    waitForNextPendingTask().task()
    cronManager.waitForCronsComplete()
    assertThat(minuteCron.counter).isEqualTo(1)

    // Cluster weight is set to 0, so now the cron will not run.
    fakeClusterWeight.setClusterWeight(0)
    clock.add(Duration.ofMinutes(1))
    waitForNextPendingTask().task()
    cronManager.waitForCronsComplete()
    assertThat(minuteCron.counter).isEqualTo(1)
  }

  private fun waitForNextPendingTask(): DelayedTask {
    return retry(RetryConfig.Builder(5, FlatBackoff(Duration.ofMillis(200))).build()) {
      pendingTasks.peekPending()!!
    }
  }
}

@Singleton
@CronPattern("* * * * *")
class MinuteCron @Inject constructor() : Runnable {
  var counter = 0

  override fun run() {
    counter++
  }
}

@Singleton
@CronPattern("0 * * * *")
class HourCron @Inject constructor() : Runnable {
  var counter = 0

  override fun run() {
    counter++
  }
}

@Singleton
@CronPattern("0 * * * *") // Default cron schedule of hourly.
class ConfigurableHourCron @Inject constructor() : Runnable {
  var counter = 0

  override fun run() {
    counter++
  }
}

@Singleton
@CronPattern("1/15 * * * *")
class ThrowsExceptionCron @Inject constructor() : Runnable {
  var counter = 0

  @Inject private lateinit var clock: Clock

  override fun run() {
    val currentMinute = clock.instant().atZone(ZoneId.of("America/Toronto")).minute
    assertThat(currentMinute % 15).isEqualTo(1)

    counter++
    throw IllegalStateException("Had an issue")
  }
}
