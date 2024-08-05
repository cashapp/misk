package misk.metrics.v2

import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Predicate
import io.prometheus.client.SimpleCollector
import misk.inject.KAbstractModule
import misk.testing.TestFixture
import java.util.Enumeration

internal class CollectorRegistryModule : KAbstractModule() {

  override fun configure() {
    val registry = CollectorRegistryFixture(CollectorRegistry(true))
    bind<CollectorRegistry>().toInstance(registry)
    multibind<TestFixture>().toInstance(registry)
  }
}

internal class CollectorRegistryFixture(val registry: CollectorRegistry) : CollectorRegistry(true), TestFixture {

  private val registeredCollectors = mutableSetOf<Collector>()

  override fun reset() {
    registeredCollectors.forEach {
      if (it is SimpleCollector<*>) {
        it.clear()
      }
    }
  }

  override fun register(collector: Collector) {
    registeredCollectors.add(collector)
    registry.register(collector)
  }

  override fun unregister(collector: Collector) {
    registeredCollectors.remove(collector)
    registry.unregister(collector)
  }

  override fun metricFamilySamples(): Enumeration<Collector.MetricFamilySamples> {
    return registry.metricFamilySamples()
  }

  override fun filteredMetricFamilySamples(includedNames: Set<String?>?): Enumeration<Collector.MetricFamilySamples> {
    return registry.filteredMetricFamilySamples(includedNames)
  }

  override fun filteredMetricFamilySamples(sampleNameFilter: Predicate<String?>?): Enumeration<Collector.MetricFamilySamples> {
    return registry.filteredMetricFamilySamples(sampleNameFilter)
  }

  override fun getSampleValue(name: String): Double? {
    return registry.getSampleValue(name)
  }

  override fun getSampleValue(
    name: String,
    labelNames: Array<String?>?,
    labelValues: Array<String?>?
  ): Double? {
    return registry.getSampleValue(name, labelNames, labelValues)
  }

  override fun clear() {
    registry.clear()
  }
}
