package misk.tracing.backends.jaeger

import com.google.inject.Provides
import com.google.inject.Singleton
import com.uber.jaeger.Configuration
import io.opentracing.Tracer
import misk.ServiceModule
import misk.config.AppName
import misk.inject.KAbstractModule

class JaegerBackendModule(val config: JaegerBackendConfig?) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<JaegerTracingService>())
  }

  @Provides
  @Singleton
  fun jaegerTracer(@AppName appName: String): Tracer {
    val reporter =
        if (config?.reporter == null) Configuration.ReporterConfiguration()
        else Configuration.ReporterConfiguration(
            config.reporter.log_spans, config.reporter.agent_host,
            config.reporter.agent_port, config.reporter.flush_interval_ms,
            config.reporter.max_queue_size)

    val sampler =
        if (config?.sampler == null) Configuration.SamplerConfiguration()
        else Configuration.SamplerConfiguration(
            config.sampler.type, config.sampler.param, config.sampler.manager_host_port)

    return Configuration(appName, sampler, reporter)
        .tracerBuilder
        .withScopeManager(MDCScopeManager())
        .build()
  }
}
