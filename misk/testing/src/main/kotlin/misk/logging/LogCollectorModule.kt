package misk.logging

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to

class LogCollectorModule : KAbstractModule() {
  override fun configure() {
    bind<LogCollector>().to<RealLogCollector>()
    binder().addMultibinderBinding<Service>().to<RealLogCollector>()
  }
}
