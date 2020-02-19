package misk.tracing.backends.jaeger

import com.google.inject.Provides
import com.google.inject.Singleton
import io.jaegertracing.Configuration
import io.opentracing.Tracer
import misk.ServiceModule
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.sampling.Sampler

class JaegerBackendModule(val config: JaegerBackendConfig?) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<JaegerTracingService>())
  }

  @Provides
  @Singleton
  fun jaegerTracer(@AppName appName: String): Tracer {
    val reporter =
        if (config?.reporter == null) Configuration.ReporterConfiguration()
        else Configuration.ReporterConfiguration()
            .withLogSpans(config.reporter.log_spans)
            .withSender(Configuration.SenderConfiguration()
                .withAgentHost(config.reporter.agent_host)
                .withAgentPort(config.reporter.agent_port)
            )
            .withFlushInterval(config.reporter.flush_interval_ms)
            .withMaxQueueSize(config.reporter.max_queue_size)

    val sampler =
        if (config?.sampler == null) Configuration.SamplerConfiguration()
        else Configuration.SamplerConfiguration()
            .withType(config.sampler.type)
            .withParam(config.sampler.param)
            .withManagerHostPort(config.sampler.manager_host_port)

    return Configuration(appName)
        .withSampler(sampler)
        .withReporter(reporter)
        .tracerBuilder
        .withScopeManager(MDCScopeManager())
        .build()
  }

  @Provides
  @Singleton
  @Tracing
  fun sampler(tracer: Tracer): Sampler {
    val samplingRate = config?.sampler?.param ?: Configuration.SamplerConfiguration().param

    return SpanSampler(tracer = tracer, samplingRate = samplingRate.toDouble())
  }
}
