package misk.logging

import misk.ServiceModule
import misk.inject.KAbstractModule

class LogCollectorModule : KAbstractModule() {
  override fun configure() {
    bind<LogCollector>().to<RealLogCollector>() // don't remove this until old interface removed
    bind<wisp.logging.LogCollector>().to<RealLogCollector>()
    bind<LogCollectorService>().to<RealLogCollector>()
    install(ServiceModule<LogCollectorService>())
  }
}
