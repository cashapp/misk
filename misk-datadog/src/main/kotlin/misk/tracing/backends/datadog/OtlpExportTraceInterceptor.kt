package misk.tracing.backends.datadog

import datadog.trace.api.GlobalTracer
import datadog.trace.api.interceptor.MutableSpan
import datadog.trace.api.interceptor.TraceInterceptor
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import misk.logging.getLogger
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val logger = getLogger<OtlpExportTraceInterceptor>()

// Every OTLP/gRPC service (metrics, traces, logs) lives under this package, so the request path of an export call
// starts with it, e.g. /opentelemetry.proto.collector.metrics.v1.MetricsService/Export.
private const val OTLP_GRPC_COLLECTOR_PATH_PREFIX = "/opentelemetry.proto.collector."
private const val HTTP_URL_TAG = "http.url"
private const val SPAN_KIND_TAG = "span.kind"
private const val SPAN_KIND_CLIENT = "client"
private const val SCHEME_SEPARATOR = "://"
private const val OTLP_ENDPOINT_PROPERTY = "otel.exporter.otlp.endpoint"
private const val OTLP_PROTOCOL_PROPERTY = "otel.exporter.otlp.protocol"
private val OTLP_SIGNALS = listOf("metrics", "traces", "logs")
// The SDK's default OTLP protocol and the http/protobuf exporters' base endpoint when none is configured.
private const val DEFAULT_OTLP_PROTOCOL = "grpc"
private val OTLP_HTTP_PROTOCOLS = setOf("http/protobuf", "http/json")
private const val DEFAULT_OTLP_HTTP_ENDPOINT = "http://localhost:4318"

/**
 * Drops Datadog traces rooted at an OTLP export call.
 *
 * dd-java-agent instruments the OkHttp client the OTLP exporters use, so every periodic metric export (every 10s per
 * process) becomes a single-span `okhttp.request` root trace to the collector. Those traces describe the telemetry
 * pipeline rather than the service, so they only add APM noise and ingestion cost. Dropping the trace in the tracer
 * also keeps it out of APM trace metrics, which sampling rules would not.
 *
 * A root span is an export when it is an HTTP client span (the exporter's outgoing call) and its `http.url` either has
 * the OTLP/gRPC service path, or is exactly one of [otlpHttpExportUrls]. Requiring a client span keeps incoming
 * requests whose path happens to match, e.g. a probe to `/opentelemetry.proto.collector.*` hitting a catch-all route,
 * visible. OTLP/HTTP paths (`/v1/metrics`) are too common to match on their own, and the collector host may also serve
 * other APIs, so only the exact export URLs the configured endpoints resolve to are dropped. Only traces whose local
 * root span is the export call are dropped; an export made inside a service trace is kept.
 *
 * @param otlpHttpExportUrls export URLs as OkHttp renders them, see [otlpHttpExportUrls].
 */
internal class OtlpExportTraceInterceptor(private val otlpHttpExportUrls: Set<String>) : TraceInterceptor {
  override fun onTraceComplete(trace: Collection<MutableSpan>): Collection<MutableSpan> {
    val root = trace.firstOrNull()?.localRootSpan ?: return trace
    if (root.getTag(SPAN_KIND_TAG)?.toString() != SPAN_KIND_CLIENT) return trace
    val url = root.getTag(HTTP_URL_TAG)?.toString() ?: return trace
    return if (isOtlpGrpcExportUrl(url) || url in otlpHttpExportUrls) emptyList() else trace
  }

  // Unique among registered interceptors (dd-trace-java silently ignores duplicates); distinct from the priorities
  // commonly used by application trace interceptors.
  override fun priority(): Int = -1_000
}

internal fun isOtlpGrpcExportUrl(url: String?): Boolean {
  if (url == null) return false
  val authorityStart = url.indexOf(SCHEME_SEPARATOR)
  if (authorityStart < 0) return false
  val pathStart = url.indexOf('/', authorityStart + SCHEME_SEPARATOR.length)
  return pathStart >= 0 && url.startsWith(OTLP_GRPC_COLLECTOR_PATH_PREFIX, pathStart)
}

/**
 * The URLs the OTLP/HTTP exporters POST to, resolved the way the OpenTelemetry SDK resolves them: a per-signal endpoint
 * is used as-is, while the generic endpoint (or the default `http://localhost:4318`) gets `/v1/<signal>` appended. Only
 * signals exported with OTLP over HTTP are included; gRPC exports don't need this, their path identifies them. Each URL
 * is rendered as OkHttp renders a request URL, which is what dd-java-agent records as `http.url`: default ports are
 * omitted, hosts lowercased and IPv6 hosts bracketed.
 */
internal fun otlpHttpExportUrls(config: ConfigProperties): Set<String> {
  val genericEndpoint =
    config.getString(OTLP_ENDPOINT_PROPERTY)?.takeIf { it.isNotBlank() } ?: DEFAULT_OTLP_HTTP_ENDPOINT
  val base = genericEndpoint.removeSuffix("/")
  val genericProtocol = config.getString(OTLP_PROTOCOL_PROPERTY)?.takeIf { it.isNotBlank() } ?: DEFAULT_OTLP_PROTOCOL
  return OTLP_SIGNALS.filter { signal ->
      "otlp" in config.getList("otel.$signal.exporter", listOf("otlp")) &&
        (config.getString("otel.exporter.otlp.$signal.protocol")?.takeIf { it.isNotBlank() } ?: genericProtocol) in
          OTLP_HTTP_PROTOCOLS
    }
    .mapNotNullTo(mutableSetOf()) { signal ->
      val endpoint =
        config.getString("otel.exporter.otlp.$signal.endpoint")?.takeIf { it.isNotBlank() } ?: "$base/v1/$signal"
      endpoint.toHttpUrlOrNull()?.newBuilder()?.query(null)?.fragment(null)?.build()?.toString()
    }
}

/**
 * Registers an [OtlpExportTraceInterceptor] with the Datadog tracer installed by dd-java-agent, so traces rooted at an
 * OpenTelemetry OTLP export call are dropped. [config] is the resolved OpenTelemetry SDK configuration, for example the
 * one `misk.opentelemetry.initializeOpenTelemetrySdk` passes to `onInitialized`; it determines the OTLP/HTTP export
 * URLs. Without a Datadog tracer this logs and does nothing.
 */
fun registerOtlpExportTraceInterceptor(config: ConfigProperties) {
  if (!GlobalTracer.get().addTraceInterceptor(OtlpExportTraceInterceptor(otlpHttpExportUrls(config)))) {
    // Usually there is no dd-java-agent tracer (agent not attached, or DD_TRACE_ENABLED=false), so nothing is traced
    // and there are no export traces to drop. A real tracer only rejects an interceptor whose priority is taken.
    logger.info {
      "Datadog tracer did not accept the OTLP export trace interceptor " +
        "(no dd-java-agent tracer installed, or its priority is already taken)"
    }
  }
}
