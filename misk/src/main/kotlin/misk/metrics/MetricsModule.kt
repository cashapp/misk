package misk.metrics

import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.BufferPoolsExports
import io.prometheus.client.hotspot.ClassLoadingExports
import io.prometheus.client.hotspot.GarbageCollectorExports
import io.prometheus.client.hotspot.MemoryPoolsExports
import io.prometheus.client.hotspot.StandardExports
import io.prometheus.client.hotspot.ThreadExports
import io.prometheus.client.hotspot.VersionInfoExports
import misk.inject.KAbstractModule

internal class MetricsModule : KAbstractModule() {
  override fun configure() {
    bind<Metrics>().asEagerSingleton()
    bind<CollectorRegistry>().toInstance(CollectorRegistry())
    multibind<Collector>().toInstance(StandardExports())
    multibind<Collector>().toInstance(MemoryPoolsExports())
    multibind<Collector>().toInstance(BufferPoolsExports())
    multibind<Collector>().toInstance(ThreadExports())
    multibind<Collector>().toInstance(GarbageCollectorExports())
    multibind<Collector>().toInstance(ClassLoadingExports())
    multibind<Collector>().toInstance(VersionInfoExports())
  }
}
