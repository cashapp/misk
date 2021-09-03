package misk.logging

import misk.ServiceModule
import misk.inject.KAbstractModule
import wisp.logging.LogCollector
import wisp.logging.WispQueuedLogCollector
import javax.inject.Provider

class LogCollectorModule : KAbstractModule() {
  override fun configure() {
    bind<LogCollector>().to<RealLogCollector>()
    bind<LogCollectorService>().to<RealLogCollector>()
    bind<WispQueuedLogCollector>().toProvider(Provider { WispQueuedLogCollector() })
    install(ServiceModule<LogCollectorService>())
  }
}
