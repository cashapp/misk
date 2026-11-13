@file:JvmName("OpenTelemetryExport")

package misk.opentelemetry

/**
 * Whether `otel.sdk.disabled`/`OTEL_SDK_DISABLED` leaves the SDK enabled. Matches OpenTelemetry autoconfigure: the
 * system property wins over the environment, and only `true` disables the SDK (unset means enabled).
 */
@JvmOverloads
fun openTelemetrySdkEnabled(
  sdkDisabled: String? = System.getProperty("otel.sdk.disabled") ?: System.getenv("OTEL_SDK_DISABLED")
): Boolean = !sdkDisabled.toBoolean()

/**
 * Whether a comma-separated `otel.metrics.exporter` list selects OTLP. Entries are trimmed and compared
 * case-insensitively; a null list selects nothing.
 */
fun isOtlpMetricsExporterSelected(metricsExporter: String?): Boolean =
  metricsExporter?.split(',')?.any { it.trim().equals("otlp", ignoreCase = true) } == true

/**
 * Whether the process-wide SDK exports metrics over OTLP: it is enabled ([openTelemetrySdkEnabled]) and the
 * `otel.metrics.exporter`/`OTEL_METRICS_EXPORTER` list selects OTLP ([isOtlpMetricsExporterSelected]). As in
 * autoconfigure, an unset or blank list defaults to `otlp`.
 */
@JvmOverloads
fun openTelemetryMetricsExporting(
  sdkDisabled: String? = System.getProperty("otel.sdk.disabled") ?: System.getenv("OTEL_SDK_DISABLED"),
  metricsExporter: String? = System.getProperty("otel.metrics.exporter") ?: System.getenv("OTEL_METRICS_EXPORTER"),
): Boolean =
  openTelemetrySdkEnabled(sdkDisabled) &&
    isOtlpMetricsExporterSelected(metricsExporter?.takeIf { it.isNotBlank() } ?: "otlp")

// OpenTelemetry Java 1.66.0 added item counts to OTLP exporter failure messages. Match the count-less 1.65.0 shape too
// while applications may still emit the previous contract. The gRPC and HTTP/protobuf exporters log the same messages
// under their own loggers.
internal const val OTLP_GRPC_EXPORTER_LOGGER = "io.opentelemetry.exporter.otlp.internal.GrpcExporter"
internal const val OTLP_HTTP_EXPORTER_LOGGER = "io.opentelemetry.exporter.otlp.internal.HttpExporter"
private val OTLP_EXPORTER_LOGGERS = setOf(OTLP_GRPC_EXPORTER_LOGGER, OTLP_HTTP_EXPORTER_LOGGER)
private const val OTLP_LEGACY_METRIC_EXPORT_FAILURE_PREFIX = "Failed to export metrics."
private const val OTLP_EXPORT_FAILURE_PREFIX = "Failed to export "
private const val OTLP_METRIC_EXPORT_FAILURE_SUFFIX = " metrics."

/**
 * Identifies OTLP metric export failures so logging can demote them to WARN, and error reporters can omit them, without
 * suppressing ordinary logs. OTLP metric export failures are best-effort noise during collector rollouts. Classifying
 * at the exporter and signal boundary avoids depending on the many response statuses and transport exception types that
 * can report the same collector failure.
 */
fun isOtlpMetricExportFailure(loggerName: String?, message: String?, throwable: Throwable?): Boolean {
  if (loggerName !in OTLP_EXPORTER_LOGGERS || message == null) return false
  val signalStart = message.indexOf(' ', OTLP_EXPORT_FAILURE_PREFIX.length)
  return message.startsWith(OTLP_LEGACY_METRIC_EXPORT_FAILURE_PREFIX) ||
    (message.startsWith(OTLP_EXPORT_FAILURE_PREFIX) &&
      signalStart > OTLP_EXPORT_FAILURE_PREFIX.length &&
      message.startsWith(OTLP_METRIC_EXPORT_FAILURE_SUFFIX, signalStart))
}
