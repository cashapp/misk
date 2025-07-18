package misk.logging

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.testing.TestFixture

class LogCollectorModule : KAbstractModule() {
  override fun configure() {
    bind<LogCollector>().to<QueuedLogCollector>()
    bind<LogCollectorService>().to<QueuedLogCollector>()
    multibind<TestFixture>().to<QueuedLogCollector>()
    install(ServiceModule<LogCollectorService>().enhancedBy<ReadyService>())
  }
}
