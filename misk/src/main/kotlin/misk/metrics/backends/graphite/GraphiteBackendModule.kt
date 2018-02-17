package misk.metrics.backends.graphite

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.Graphite
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.graphite.GraphiteSender
import com.google.common.util.concurrent.Service
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.config.AppName
import misk.environment.InstanceMetadata
import misk.inject.addMultibinderBinding
import misk.inject.to
import misk.metrics.Metrics
import java.util.concurrent.TimeUnit

class GraphiteBackendModule : AbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<Service>()
        .to<GraphiteReporterService>()
  }

  @Provides
  @Singleton
  fun graphiteSender(config: GraphiteBackendConfig): GraphiteSender =
      Graphite(config.host, config.port)

  @Provides
  @Singleton
  fun graphiteReporter(
      @AppName appName: String,
      instanceMetadata: InstanceMetadata,
      metricRegistry: MetricRegistry,
      sender: GraphiteSender
  ): GraphiteReporter {

    val instanceName = Metrics.sanitize(instanceMetadata.instanceName)
    val zone = Metrics.sanitize(instanceMetadata.zone)
    val prefix = "$appName.$zone.$instanceName"

    return GraphiteReporter.forRegistry(metricRegistry)
        .prefixedWith(prefix)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .convertRatesTo(TimeUnit.MILLISECONDS)
        .build(sender)
  }

}
