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
 * Whether the process-wide SDK exports metrics: it is enabled ([openTelemetrySdkEnabled]) and its metrics exporter list
 * names an exporter other than `none`. Once [initializeOpenTelemetrySdk] has built the SDK this is its resolved
 * configuration, including properties its `customize` supplied; before that it reads `otel.sdk.disabled` and
 * `otel.metrics.exporter` from system properties and the environment.
 */
fun openTelemetryMetricsExporting(): Boolean =
  initializedMetricsExporting
    ?: openTelemetryMetricsExporting(
      sdkDisabled = System.getProperty("otel.sdk.disabled") ?: System.getenv("OTEL_SDK_DISABLED"),
      metricsExporter = System.getProperty("otel.metrics.exporter") ?: System.getenv("OTEL_METRICS_EXPORTER"),
    )

/**
 * Whether an SDK configured with these `otel.sdk.disabled` and `otel.metrics.exporter` values exports metrics. As in
 * autoconfigure, an unset or blank exporter list defaults to `otlp`.
 */
fun openTelemetryMetricsExporting(sdkDisabled: String?, metricsExporter: String?): Boolean =
  openTelemetrySdkEnabled(sdkDisabled) && exportsMetrics(metricsExporter)

internal fun exportsMetrics(metricsExporter: String?): Boolean {
  val exporters = metricsExporter.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
  return exporters.isEmpty() || exporters.any { !it.equals("none", ignoreCase = true) }
}

// OpenTelemetry Java 1.66.0 added item counts to OTLP exporter failure messages. Match the count-less 1.65.0 shape too
// while applications may still emit the previous contract. The gRPC and HTTP/protobuf exporters log the same messages
// under their own loggers.
internal const val OTLP_GRPC_EXPORTER_LOGGER = "io.opentelemetry.exporter.otlp.internal.GrpcExporter"
internal const val OTLP_HTTP_EXPORTER_LOGGER = "io.opentelemetry.exporter.otlp.internal.HttpExporter"
private val OTLP_EXPORTER_LOGGERS = setOf(OTLP_GRPC_EXPORTER_LOGGER, OTLP_HTTP_EXPORTER_LOGGER)
private const val OTLP_LEGACY_METRIC_EXPORT_FAILURE_PREFIX = "Failed to export metrics."
private const val OTLP_EXPORT_FAILURE_PREFIX = "Failed to export "
private const val OTLP_METRIC_EXPORT_FAILURE_SUFFIX = " metrics."

// After 5 failures a minute the exporters' ThrottlingLogger logs this notice, at the failures' level, and then only one
// failure a minute.
private const val OTLP_EXPORTER_THROTTLE_NOTICE_PREFIX = "Too many log messages detected."

/**
 * Identifies OTLP metric export failures so logging can demote them to WARN, and error reporters can omit them, without
 * suppressing ordinary logs. OTLP metric export failures are best-effort noise during collector rollouts. Classifying
 * at the exporter and signal boundary avoids depending on the many response statuses and transport exception types that
 * can report the same collector failure.
 *
 * The exporters' throttling notice is also matched. It does not name a signal, so with trace or log export enabled it
 * may follow failures of those signals instead.
 */
fun isOtlpMetricExportFailure(loggerName: String?, message: String?, throwable: Throwable?): Boolean {
  if (loggerName !in OTLP_EXPORTER_LOGGERS || message == null) return false
  if (message.startsWith(OTLP_EXPORTER_THROTTLE_NOTICE_PREFIX)) return true
  val signalStart = message.indexOf(' ', OTLP_EXPORT_FAILURE_PREFIX.length)
  return message.startsWith(OTLP_LEGACY_METRIC_EXPORT_FAILURE_PREFIX) ||
    (message.startsWith(OTLP_EXPORT_FAILURE_PREFIX) &&
      signalStart > OTLP_EXPORT_FAILURE_PREFIX.length &&
      message.startsWith(OTLP_METRIC_EXPORT_FAILURE_SUFFIX, signalStart))
}
