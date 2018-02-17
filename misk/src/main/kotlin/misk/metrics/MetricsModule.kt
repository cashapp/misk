package misk.metrics

import com.codahale.metrics.MetricRegistry
import misk.inject.KAbstractModule

class MetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().asEagerSingleton()
    bind<MetricRegistry>().asEagerSingleton()
  }
}
