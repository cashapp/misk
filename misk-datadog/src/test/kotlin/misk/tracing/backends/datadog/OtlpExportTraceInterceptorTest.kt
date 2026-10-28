package misk.tracing.backends.datadog

import datadog.trace.api.interceptor.MutableSpan
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties
import java.lang.reflect.Proxy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OtlpExportTraceInterceptorTest {
  private val interceptor = OtlpExportTraceInterceptor(setOf("http://collector:4318/v1/metrics"))

  @Test
  fun `drops traces rooted at an OTLP gRPC export`() {
    assertThat(interceptor.onTraceComplete(listOf(span(METRICS_EXPORT_URL)))).isEmpty()
  }

  @Test
  fun `drops traces rooted at a configured OTLP HTTP export url`() {
    assertThat(interceptor.onTraceComplete(listOf(span("http://collector:4318/v1/metrics")))).isEmpty()
  }

  @Test
  fun `keeps other calls to the collector host`() {
    val trace = listOf(span("http://collector:4318/orders"))

    assertThat(interceptor.onTraceComplete(trace)).isSameAs(trace)
  }

  @Test
  fun `keeps OTLP HTTP paths sent to other hosts`() {
    val trace = listOf(span("http://api.example:4318/v1/metrics"))

    assertThat(interceptor.onTraceComplete(trace)).isSameAs(trace)
  }

  @Test
  fun `keeps service traces that contain an OTLP export`() {
    val root = span("http://localhost:8080/api/thing")
    val trace = listOf(root, span(METRICS_EXPORT_URL, root), span("http://collector:4318/v1/metrics", root))

    assertThat(interceptor.onTraceComplete(trace)).isSameAs(trace)
  }

  @Test
  fun `keeps traces whose root has no http url`() {
    val trace = listOf(span(null))

    assertThat(interceptor.onTraceComplete(trace)).isSameAs(trace)
  }

  @Test
  fun `keeps server traces whose path matches an OTLP export`() {
    val grpcPath = listOf(span("https://app.example/opentelemetry.proto.collector.probe", kind = "server"))
    val httpUrl = listOf(span("http://collector:4318/v1/metrics", kind = "server"))

    assertThat(interceptor.onTraceComplete(grpcPath)).isSameAs(grpcPath)
    assertThat(interceptor.onTraceComplete(httpUrl)).isSameAs(httpUrl)
  }

  @Test
  fun `keeps export urls on roots without a span kind`() {
    val trace = listOf(span(METRICS_EXPORT_URL, kind = null))

    assertThat(interceptor.onTraceComplete(trace)).isSameAs(trace)
  }

  @Test
  fun `matches only the OTLP collector service path`() {
    assertThat(isOtlpGrpcExportUrl(METRICS_EXPORT_URL)).isTrue()
    assertThat(isOtlpGrpcExportUrl("https://collector:4317/opentelemetry.proto.collector.trace.v1.TraceService/Export"))
      .isTrue()
    assertThat(isOtlpGrpcExportUrl("http://collector:4318/v1/metrics")).isFalse()
    assertThat(isOtlpGrpcExportUrl("http://host/api?next=/opentelemetry.proto.collector.metrics.v1")).isFalse()
    assertThat(isOtlpGrpcExportUrl("http://host/proxy/opentelemetry.proto.collector.metrics.v1")).isFalse()
    assertThat(isOtlpGrpcExportUrl("/opentelemetry.proto.collector.metrics.v1.MetricsService/Export")).isFalse()
    assertThat(isOtlpGrpcExportUrl("http://opentelemetry.proto.collector.example")).isFalse()
  }

  @Test
  fun `grpc exports add no http export urls`() {
    assertThat(otlpHttpExportUrls(config())).isEmpty()
    assertThat(otlpHttpExportUrls(config("otel.exporter.otlp.metrics.endpoint" to "https://api.example"))).isEmpty()
  }

  @Test
  fun `http export urls default to the localhost http endpoint`() {
    assertThat(otlpHttpExportUrls(config(HTTP)))
      .containsExactlyInAnyOrder(
        "http://localhost:4318/v1/metrics",
        "http://localhost:4318/v1/traces",
        "http://localhost:4318/v1/logs",
      )
  }

  @Test
  fun `http export urls append the signal path to the generic endpoint`() {
    assertThat(otlpHttpExportUrls(config(HTTP, "otel.exporter.otlp.endpoint" to "https://api.example/otel/")))
      .containsExactlyInAnyOrder(
        "https://api.example/otel/v1/metrics",
        "https://api.example/otel/v1/traces",
        "https://api.example/otel/v1/logs",
      )
  }

  @Test
  fun `signal endpoints are used as-is`() {
    val config =
      config(
        HTTP,
        "otel.exporter.otlp.endpoint" to "http://collector:4318",
        "otel.exporter.otlp.metrics.endpoint" to "https://api.example/otel/v1/metrics",
        "otel.exporter.otlp.logs.endpoint" to "http://logs-collector:4318",
      )

    assertThat(otlpHttpExportUrls(config))
      .containsExactlyInAnyOrder(
        "https://api.example/otel/v1/metrics",
        "http://collector:4318/v1/traces",
        "http://logs-collector:4318/",
      )
  }

  @Test
  fun `only signals exported over otlp http contribute urls`() {
    val config =
      config(
        "otel.exporter.otlp.metrics.protocol" to "http/protobuf",
        "otel.exporter.otlp.traces.protocol" to "http/json",
        "otel.exporter.otlp.logs.protocol" to "http/protobuf",
        "otel.traces.exporter" to "otlp,console",
        "otel.logs.exporter" to "none",
      )

    assertThat(otlpHttpExportUrls(config))
      .containsExactlyInAnyOrder("http://localhost:4318/v1/metrics", "http://localhost:4318/v1/traces")
  }

  @Test
  fun `signal protocols override the generic protocol`() {
    val config =
      config(
        HTTP,
        "otel.exporter.otlp.metrics.protocol" to "grpc",
        "otel.exporter.otlp.metrics.endpoint" to "https://api.example",
      )

    assertThat(otlpHttpExportUrls(config))
      .containsExactlyInAnyOrder("http://localhost:4318/v1/traces", "http://localhost:4318/v1/logs")
  }

  @Test
  fun `http export urls render as OkHttp renders request urls`() {
    val config =
      config(
        HTTP,
        "otel.exporter.otlp.metrics.endpoint" to "http://Collector:80/v1/metrics?x=1",
        "otel.exporter.otlp.traces.endpoint" to "https://collector:443/v1/traces",
        "otel.exporter.otlp.logs.endpoint" to "http://[::1]:4318/v1/logs",
      )

    assertThat(otlpHttpExportUrls(config))
      .containsExactlyInAnyOrder(
        "http://collector/v1/metrics",
        "https://collector/v1/traces",
        "http://[::1]:4318/v1/logs",
      )
  }

  private fun config(vararg properties: Pair<String, String>) =
    DefaultConfigProperties.createFromMap(mapOf(*properties))

  private fun span(httpUrl: String?, localRoot: MutableSpan? = null, kind: String? = "client"): MutableSpan {
    lateinit var span: MutableSpan
    span =
      Proxy.newProxyInstance(MutableSpan::class.java.classLoader, arrayOf(MutableSpan::class.java)) { _, method, args ->
        when (method.name) {
          "getTag" ->
            when (args[0]) {
              "http.url" -> httpUrl
              "span.kind" -> kind
              else -> null
            }
          "getLocalRootSpan" -> localRoot ?: span
          else -> throw UnsupportedOperationException(method.name)
        }
      } as MutableSpan
    return span
  }

  private companion object {
    const val METRICS_EXPORT_URL =
      "http://otel-collector.example.com:4317/" + "opentelemetry.proto.collector.metrics.v1.MetricsService/Export"
    val HTTP = "otel.exporter.otlp.protocol" to "http/protobuf"
  }
}
