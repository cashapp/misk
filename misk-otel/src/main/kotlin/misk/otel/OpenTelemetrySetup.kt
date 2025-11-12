package misk.otel

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.instrumentation.runtimemetrics.java8.Classes
import io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu
import io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector
import io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools
import io.opentelemetry.instrumentation.runtimemetrics.java8.Threads
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.ServiceAttributes
import java.time.Duration

fun setupOpenTelemetry(
  serviceName: String,
  serviceVersion: String? = null,
  otlpEndpoint: String? = null,
  metricExportInterval: Duration = Duration.ofMinutes(1),
  additionalAttributes: Attributes = Attributes.empty()
) {
  val resourceBuilder = Resource.getDefault().toBuilder()
    .put(ServiceAttributes.SERVICE_NAME, serviceName)

  if (serviceVersion != null) {
    resourceBuilder.put(ServiceAttributes.SERVICE_VERSION, serviceVersion)
  }

  additionalAttributes.forEach { attributeKey, attributeValue ->
    @Suppress("UNCHECKED_CAST")
    resourceBuilder.put(attributeKey as io.opentelemetry.api.common.AttributeKey<Any>, attributeValue)
  }

  val resource = resourceBuilder.build()

  val meterProvider = SdkMeterProvider.builder()
    .setResource(resource)
    .apply {
      if (otlpEndpoint != null) {
        registerMetricReader(
          PeriodicMetricReader.builder(
            OtlpGrpcMetricExporter.builder()
              .setEndpoint(otlpEndpoint)
              .build()
          )
            .setInterval(metricExportInterval)
            .build()
        )
      }
    }
    .build()

  val openTelemetry = OpenTelemetrySdk.builder()
    .setMeterProvider(meterProvider)
    .build()

  GlobalOpenTelemetry.set(openTelemetry)

  Classes.registerObservers(openTelemetry)
  Cpu.registerObservers(openTelemetry)
  GarbageCollector.registerObservers(openTelemetry)
  MemoryPools.registerObservers(openTelemetry)
  Threads.registerObservers(openTelemetry)

  val javaVersion = System.getProperty("java.version")
  if (javaVersion.startsWith("17") || javaVersion.split(".")[0].toIntOrNull()?.let { it >= 17 } == true) {
    RuntimeMetrics.builder(openTelemetry).build()
  }
}
