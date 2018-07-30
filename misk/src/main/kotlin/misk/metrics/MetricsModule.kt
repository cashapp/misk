package misk.metrics

import io.prometheus.client.CollectorRegistry
import misk.inject.KAbstractModule

internal class MetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().asEagerSingleton()
    bind<CollectorRegistry>().asEagerSingleton()
  }
}
