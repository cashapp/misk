package misk.tracing.backends.datadog

import datadog.trace.api.GlobalTracer
import misk.inject.KAbstractModule
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracerFactory
import misk.logging.getLogger

/**
 * Binds the datadog tracer to opentracing's [Tracer]
 */
class DatadogTracingBackendModule : KAbstractModule() {
  override fun configure() {
    // A DDTracer is installed by the DataDog Java agent, which runs before the app's main() method.
    val tracer = GlobalTracer.get()
    if (tracer is Tracer) {
      bind<Tracer>().toInstance(tracer)
    } else {
      logger.error("A DDTracer was unexpectedly not installed by the java agent. "
          + "Are you missing this environment in your `dd_agent_env_whitelist` config in "
          + "app-manifest.yaml?")
      bind(Tracer::class.java).toInstance(NoopTracerFactory.create())
    }
  }

  companion object {
    val logger = getLogger<DatadogTracingBackendModule>()
  }
}