package misk.cron

import misk.inject.KAbstractModule
import misk.jobqueue.FakeJobQueue
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

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
    }
  }

  @Inject private lateinit var cronManager: CronManager
  @Inject private lateinit var clock: FakeClock
  @Inject private lateinit var fakeJobQueue: FakeJobQueue

  @Inject private lateinit var minuteCron: MinuteCron
  @Inject private lateinit var hourCron: HourCron
  @Inject private lateinit var throwsExceptionCron: ThrowsExceptionCron

  private fun runCrons() {
    cronManager.runReadyCrons()
    fakeJobQueue.handleJobs()
  }

  @Test
  fun basic() {
    assertThat(minuteCron.counter).isEqualTo(0)
    assertThat(hourCron.counter).isEqualTo(0)
    assertThat(throwsExceptionCron.counter).isEqualTo(0)

    // Should not be ready to run yet.
    runCrons()
    assertThat(minuteCron.counter).isEqualTo(0)
    assertThat(hourCron.counter).isEqualTo(0)
    assertThat(throwsExceptionCron.counter).isEqualTo(0)

    // Advance one hour in one minute intervals.
    repeat(60) {
      clock.add(Duration.ofMinutes(1))
      runCrons()
    }
    assertThat(minuteCron.counter).isEqualTo(60)
    assertThat(throwsExceptionCron.counter).isEqualTo(4)
    assertThat(hourCron.counter).isEqualTo(1)
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
