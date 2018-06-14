package misk.logging

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding

class LogCollectorModule : KAbstractModule() {
  override fun configure() {
    bind(LogCollector::class.java).to(RealLogCollector::class.java)
    binder().addMultibinderBinding<Service>().to(RealLogCollector::class.java)
  }
}
