package misk.metrics.v2

import misk.inject.KAbstractModule

/** In-memory metrics backends, including an injector-local OpenTelemetry SDK and metric reader. */
class FakeMetricsModule : KAbstractModule() {
  override fun configure() {
    install(CollectorRegistryModule())
    install(OpenTelemetryTestingModule())
    bind<Metrics>().to<FakeMetrics>()
  }
}
