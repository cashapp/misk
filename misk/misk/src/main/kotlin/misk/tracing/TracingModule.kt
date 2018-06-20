package misk.tracing

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.tracing.backends.TracingBackendConfig
import misk.tracing.backends.jaeger.JaegerBackendModule
import misk.tracing.backends.noop.NoopTracerBackendModule
import misk.tracing.backends.zipkin.ZipkinBackendModule
import kotlin.reflect.full.memberProperties

private val logger = getLogger<TracingModule>()

class TracingModule(val config: TracingConfig) : KAbstractModule() {
  override fun configure() {
    multibind<Service>().to<TracingService>()

    val configuredTracers = TracingBackendConfig::class.memberProperties.mapNotNull { it.get(config.backends) }

    check(configuredTracers.size < 2) { "More than one tracer has been configured. Please remove one." }

    when {
      config.backends.jaeger != null -> install(JaegerBackendModule(config.backends.jaeger))
      config.backends.zipkin != null -> install(ZipkinBackendModule(config.backends.zipkin))
      else -> {
        install(NoopTracerBackendModule())
        logger.info("No tracing backend configured. NoopTracer installed.")
      }
    }
  }
}
