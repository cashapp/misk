package misk.perf

import misk.ServiceModule
import misk.inject.KAbstractModule

/**
 * Install this module to run the [PauseDetector] in the background.
 */
class PauseDetectorModule constructor(
  val pauseDetectorConfig: PauseDetectorConfig = PauseDetectorConfig(),
) : KAbstractModule() {

  override fun configure() {
    install(ServiceModule<PauseDetector>())
    bind<PauseDetectorConfig>().toInstance(pauseDetectorConfig)
  }
}
