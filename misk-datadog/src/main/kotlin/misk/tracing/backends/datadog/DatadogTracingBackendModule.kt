package misk.tracing.backends.datadog

import com.squareup.cash.tracing.datadog.MDCScopeListener
import datadog.trace.api.internal.InternalTracer
import io.opentracing.Tracer
import misk.inject.KAbstractModule

/**
 * Binds the datadog tracer to opentracing's [Tracer]
 */
class DatadogTracingBackendModule : KAbstractModule() {
  override fun configure() {
    // A DDTracer is installed by the dd-java-agent in TracerInstaller, which runs before the app's main() method.
    // Otherwise, the GlobalTracer in both libraries would return a noop tracer and tracing would be effectively disabled.
    // See https://docs.datadoghq.com/tracing/custom_instrumentation/java/
    // See https://github.com/DataDog/dd-trace-java/tree/v0.65.0/dd-smoke-tests/opentracing/src/main/java/datadog/smoketest/opentracing
    bind<Tracer>().toInstance(io.opentracing.util.GlobalTracer.get())
    getInternalTracer()?.apply {
      addScopeListener(MDCScopeListener.Activated(), MDCScopeListener.Closed())
    }
  }

  private fun getInternalTracer() = datadog.trace.api.GlobalTracer.get() as? InternalTracer
}
