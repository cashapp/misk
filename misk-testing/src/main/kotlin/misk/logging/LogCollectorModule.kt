package misk.logging

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import wisp.logging.LogCollector
import wisp.logging.WispQueuedLogCollector
import com.google.inject.Provider
import misk.testing.TestFixture

class LogCollectorModule : KAbstractModule() {
  override fun configure() {
    bind<LogCollector>().to<RealLogCollector>()
    bind<LogCollectorService>().to<RealLogCollector>()
    multibind<TestFixture>().to<RealLogCollector>()
    bind<WispQueuedLogCollector>().toProvider { WispQueuedLogCollector() }
    multibind<TestFixture>().to<WispQueuedLogCollector>()
    install(ServiceModule<LogCollectorService>().enhancedBy<ReadyService>())
  }
}
