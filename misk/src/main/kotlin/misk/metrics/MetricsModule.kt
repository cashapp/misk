package misk.metrics

import com.codahale.metrics.MetricRegistry
import com.google.inject.AbstractModule
import com.google.inject.Singleton

class MetricsModule : AbstractModule() {
  override fun configure() {
    bind(MetricRegistry::class.java).toInstance(MetricRegistry())
    bind(Metrics::class.java).`in`(Singleton::class.java)
  }
}
