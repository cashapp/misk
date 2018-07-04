package misk.logging

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule

class LogCollectorModule : KAbstractModule() {
  override fun configure() {
    bind<LogCollector>().to<RealLogCollector>()
    multibind<Service>().to<RealLogCollector>()
  }
}
