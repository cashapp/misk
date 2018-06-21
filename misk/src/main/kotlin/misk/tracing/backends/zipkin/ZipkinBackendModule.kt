package misk.tracing.backends.zipkin

import brave.Tracing
import brave.opentracing.BraveTracer
import com.google.inject.Provides
import io.opentracing.Tracer
import misk.config.AppName
import misk.inject.KAbstractModule
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.okhttp3.OkHttpSender
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class ZipkinBackendModule(val config: ZipkinBackendConfig) : KAbstractModule() {
  override fun configure() {}

  @Provides
  @Singleton
  fun zipkinTracer(@AppName appName: String): Tracer {
    val reporter = AsyncReporter.builder(OkHttpSender.create(config.collector_url))

    if (config.reporter?.message_max_bytes != null)
      reporter.messageMaxBytes(config.reporter.message_max_bytes)
    if (config.reporter?.message_timeout_sec != null)
      reporter.messageTimeout(config.reporter.message_timeout_sec, TimeUnit.SECONDS)
    if (config.reporter?.queued_max_bytes != null)
      reporter.queuedMaxBytes(config.reporter.queued_max_bytes)

    return BraveTracer.create(Tracing.newBuilder()
        .spanReporter(reporter.build())
        .localServiceName(appName)
        .build())
  }
}
