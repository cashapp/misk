package misk.perf

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.google.common.base.Ticker
import com.google.inject.Key
import misk.ServiceManagerModule
import misk.concurrent.FakeTicker
import misk.concurrent.Sleeper
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.logging.LogCollectorModule
import misk.metrics.v2.FakeMetricsModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true) // NB: only starting services here to get log collection to work.
class PauseDetectorTest {
  @MiskTestModule val module = TestModule()

  @Inject @ForPauseDetector lateinit var fakeTicker: FakeTicker
  @Inject internal lateinit var detector: PauseDetector
  @Inject lateinit var logCollector: LogCollector

  @BeforeEach fun setup() {
    // We'll drive the detector from the test thread rather than running a detector thread.
    assertThat(detector.isRunning()).isFalse()

    // Do one cycle to calibrate shortestObservedDeltaTimeNsec
    detector.sleep()
    val (pauseTimeMillis, shortestObservedDeltaNsec) = detector.check()
    assertThat(pauseTimeMillis).isEqualTo(0)
    verifyShortestObservedDelta(shortestObservedDeltaNsec)
    assertThat(takeLogs()).isEmpty()
  }

  private fun verifyShortestObservedDelta(shortestObservedDeltaNsec: Long) {
    assertThat(shortestObservedDeltaNsec).isEqualTo(TimeUnit.MILLISECONDS.toNanos(1))
  }

  @Test fun `no logging`() {
    detector.sleep()
    fakeTicker.sleepMs(99)
    detector.check()
    takeLogs().isEmpty()
  }

  @Test fun `log info on a pause`() {
    detector.sleep()
    fakeTicker.sleepMs(101)
    val (pauseTimeMillis, shortestObservedDeltaNsec) = detector.check()
    assertThat(pauseTimeMillis).isEqualTo(101)
    verifyShortestObservedDelta(shortestObservedDeltaNsec)
    val logs = takeLogs()
    assertThat(logs).hasSize(1)
    assertThat(logs.first().message).isEqualTo("Detected JVM pause of 101 ms")
    assertThat(logs.first().level).isEqualTo(Level.INFO)
  }

  @Test fun `log warn on a pause`() {
    detector.sleep()
    fakeTicker.sleepMs(2322)
    val (pauseTimeMillis, shortestObservedDeltaNsec) = detector.check()
    assertThat(pauseTimeMillis).isEqualTo(2322)
    verifyShortestObservedDelta(shortestObservedDeltaNsec)
    val logs = takeLogs()
    assertThat(logs).hasSize(1)
    assertThat(logs.first().message).isEqualTo("Detected JVM pause of 2322 ms")
    assertThat(logs.first().level).isEqualTo(Level.WARN)
  }

  @Test fun `log error on a pause`() {
    detector.sleep()
    fakeTicker.sleepMs(99999)
    val (pauseTimeMillis, shortestObservedDeltaNsec) = detector.check()
    assertThat(pauseTimeMillis).isEqualTo(99999)
    verifyShortestObservedDelta(shortestObservedDeltaNsec)
    val logs = takeLogs()
    assertThat(logs).hasSize(1)
    assertThat(logs.first().message).isEqualTo("Detected JVM pause of 99999 ms")
    assertThat(logs.first().level).isEqualTo(Level.ERROR)
  }

  @Test fun `ticker goes backwards`() {
    // Move forward 5s so that we can move _backwards_ to a positive value in the test.
    fakeTicker.sleepMs(5000);

    // 1ms of sleep
    detector.sleep()
    // Move the ticker back 2020ms
    fakeTicker.sleepMs(-2020)
    val (pauseTimeMillis, shortestObservedDeltaNsec) = detector.check()
    assertThat(pauseTimeMillis).isEqualTo(-2020L)
    verifyShortestObservedDelta(shortestObservedDeltaNsec)
    val logs = takeLogs()
    assertThat(logs).hasSize(1)
    assertThat(logs.first().message)
      .isEqualTo("Observed a negative pause time of 2020ms. Non-monotonic ticker?")
    assertThat(logs.first().level).isEqualTo(Level.INFO)
  }

  private fun takeLogs(): List<ILoggingEvent> = logCollector.takeEvents(PauseDetector::class)

  class TestModule : KAbstractModule() {
    override fun configure() {
      // Wire up the detector
      val config = PauseDetectorConfig(
        resolutionMillis = 1,
        logInfoMillis = 100,
        logWarnMillis = 1000,
        logErrorMillis = 10000
      )
      // NB: We are intentionally _not_ installing the module
      // because we want to drive the detector check/sleep cycles from this test harness and
      // we want to configure a fake ticker.
      bind<PauseDetectorConfig>().toInstance(config)
      bind<FakeTicker>().annotatedWith<ForPauseDetector>().toInstance(FakeTicker())
      bind<Ticker>().annotatedWith<ForPauseDetector>()
        .to(Key.get(FakeTicker::class.java, ForPauseDetector::class.java))
      bind<Sleeper>().annotatedWith<ForPauseDetector>()
        .to(Key.get(FakeTicker::class.java, ForPauseDetector::class.java))

      // And its dependencies with test fakes
      install(ServiceManagerModule())
      install(FakeMetricsModule())

      // Test support
      install(LogCollectorModule())
    }
  }
}
