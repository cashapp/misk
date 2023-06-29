package misk.perf

import com.google.common.base.Ticker
import misk.ServiceModule
import misk.concurrent.Sleeper
import misk.inject.KAbstractModule

/**
 * Install this module to run the [PauseDetector] in the background.
 */
class PauseDetectorModule(
  val pauseDetectorConfig: PauseDetectorConfig = PauseDetectorConfig(),
) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<PauseDetector>())
    bind<PauseDetectorConfig>().toInstance(pauseDetectorConfig)

    // Outside of pause detector's own testing harness, we don't want to run with a fake sleeper.
    // A fake sleeper might never return from `sleep` which will cause service teardown errors.
    // Another kind of fake sleeper might return immediately from sleep, but that can waste
    // CPU cycles in CI if the pause detector is pulled into such a test.
    bind<Sleeper>().annotatedWith<ForPauseDetector>().toInstance(Sleeper.DEFAULT)
    // For good measure, bind a real ticker with the real sleeper:
    bind<Ticker>().annotatedWith<ForPauseDetector>().toInstance(Ticker.systemTicker())
  }
}
