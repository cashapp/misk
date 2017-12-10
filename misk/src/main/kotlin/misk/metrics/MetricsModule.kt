package misk.metrics

import com.codahale.metrics.MetricRegistry
import com.google.inject.AbstractModule

class MetricsModule : AbstractModule() {
    override fun configure() {
        bind(Metrics::class.java).asEagerSingleton()
        bind(MetricRegistry::class.java).asEagerSingleton()
    }
}