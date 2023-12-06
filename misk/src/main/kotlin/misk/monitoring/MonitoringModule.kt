package misk.monitoring

import misk.inject.KInstallOnceModule

class MonitoringModule : KInstallOnceModule() {
  override fun configure() {
    // Use an eager binding to force instantiation of this otherwise unused class
    bind<JvmMetrics>().asEagerSingleton()
  }
}
