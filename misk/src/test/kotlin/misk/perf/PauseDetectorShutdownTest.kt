package misk.perf

import com.google.common.base.Ticker
import jakarta.inject.Inject
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.concurrent.Sleeper
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebServerTestingModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class PauseDetectorShutdownTest {
  @MiskTestModule val module = TestModule()

  @Inject internal lateinit var detector: PauseDetector

  @Test
  fun `service stops promptly when shut down`() {
    assertThat(detector.isRunning).isTrue()

    // The detector is sleeping for 10 seconds. Without triggerShutdown() interrupting the thread,
    // it would remain blocked in Thread.sleep(10000) until the sleep completes naturally.
    detector.stopAsync()
    try {
      detector.awaitTerminated(1, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
      throw AssertionError(
        "PauseDetector did not stop within 1 second. " +
          "triggerShutdown() likely isn't interrupting the sleeping thread.",
        e,
      )
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      // Use a long resolution so the thread is blocked in Thread.sleep() when we stop.
      val config = PauseDetectorConfig(resolutionMillis = 10000)
      install(ServiceModule<PauseDetector>())
      bind<PauseDetectorConfig>().toInstance(config)
      bind<Sleeper>().annotatedWith<ForPauseDetector>().toInstance(Sleeper.DEFAULT)
      bind<Ticker>().annotatedWith<ForPauseDetector>().toInstance(Ticker.systemTicker())

      install(MiskTestingServiceModule())
      install(WebServerTestingModule())
    }
  }
}
