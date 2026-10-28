@file:JvmName("OpenTelemetryInitialization")

package misk.opentelemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import java.util.concurrent.atomic.AtomicReference
import misk.logging.getLogger

private val logger = getLogger<OpenTelemetryModule>()
private const val EXPORTER_MEMORY_MODE_PROPERTY = "otel.java.exporter.memory_mode"
private const val NORMALIZED_EXPORTER_MEMORY_MODE_PROPERTY = "otel.java.exporter.memory.mode"
private const val METRICS_EXPORTER_PROPERTY = "otel.metrics.exporter"
private const val SEMCONV_STABILITY_OPT_IN_PROPERTY = "otel.semconv-stability.opt-in"
private const val SEMCONV_STABILITY_OPT_IN_ENV = "OTEL_SEMCONV_STABILITY_OPT_IN"
private const val STABLE_DATABASE_SEMCONV = "database"

/** The SDK initialized by this process's first [initializeOpenTelemetrySdk] call; later calls are no-ops. */
private val initializedSdk = AtomicReference<OpenTelemetry>()
private val initializationLock = Any()

private data class OpenTelemetryOptions(
  /** The SDK is enabled and exports metrics; otherwise every meter is a no-op. */
  val metricsExporting: Boolean = false,
  val emitExperimentalJfrMetrics: Boolean = false,
  val config: ConfigProperties? = null,
)

internal fun exporterMemoryModeOverride(config: ConfigProperties): Map<String, String> {
  val configuredValue = config.getString(EXPORTER_MEMORY_MODE_PROPERTY)
  if (configuredValue != null) return emptyMap()
  // OTel 1.66 normalizes environment-variable underscores to periods, but the exporter reads the memory_mode property
  // with an underscore.
  val environmentValue = config.getString(NORMALIZED_EXPORTER_MEMORY_MODE_PROPERTY)
  if (environmentValue.isNullOrEmpty()) return emptyMap()
  return mapOf(EXPORTER_MEMORY_MODE_PROPERTY to environmentValue)
}

internal fun stableDatabaseSemconvOptIns(configuredValue: String?): String {
  val preservedOptIns =
    configuredValue
      .orEmpty()
      .split(',')
      .map(String::trim)
      .filter(String::isNotEmpty)
      .filterNot { it == STABLE_DATABASE_SEMCONV || it == "$STABLE_DATABASE_SEMCONV/dup" }
      .distinct()
  return (preservedOptIns + STABLE_DATABASE_SEMCONV).joinToString(",")
}

private fun configureStableDatabaseSemconv() {
  val configuredValue =
    System.getProperty(SEMCONV_STABILITY_OPT_IN_PROPERTY) ?: System.getenv(SEMCONV_STABILITY_OPT_IN_ENV)
  System.setProperty(SEMCONV_STABILITY_OPT_IN_PROPERTY, stableDatabaseSemconvOptIns(configuredValue))
}

/**
 * An autoconfigure builder with Misk's base configuration: [serviceName] as the default `otel.service.name`, and
 * `OTEL_JAVA_EXPORTER_MEMORY_MODE` applied from the environment (autoconfigure normalizes its name so the exporter
 * would otherwise not see it). [initializeOpenTelemetrySdk] builds the process SDK from it; use it directly for an
 * additional, non-global SDK that should be configured the same way.
 *
 * @param serviceName used as the default OTel service name; OTEL_SERVICE_NAME overrides it.
 */
@JvmOverloads
fun openTelemetrySdkBuilder(serviceName: String? = null): AutoConfiguredOpenTelemetrySdkBuilder {
  val builder = AutoConfiguredOpenTelemetrySdk.builder()
  serviceName?.let { name -> builder.addPropertiesSupplier { mapOf("otel.service.name" to name) } }
  return builder.addPropertiesCustomizer(::exporterMemoryModeOverride)
}

/**
 * Initializes the OpenTelemetry SDK with OpenTelemetry autoconfigure, so the standard `OTEL_*` environment variables
 * and `otel.*` system properties apply with autoconfigure's defaults
 * (https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/).
 *
 * This function should be called early in the application lifecycle, before Guice binding, to ensure the SDK is
 * available globally from the start. Pass the result to [OpenTelemetryModule] to bind it for injection. Only the first
 * call in a process initializes the SDK; later calls return it and ignore their arguments.
 *
 * In addition to autoconfigure, the SDK starts from [openTelemetrySdkBuilder], and:
 * - Database instrumentation always uses stable `db.client.connection.*` semantic conventions. Other configured
 *   `OTEL_SEMCONV_STABILITY_OPT_IN` selections are preserved.
 * - JVM runtime metrics are registered on the SDK. Set
 *   `OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_JFR_METRICS=true` to add JFR-based metrics; they increase
 *   memory usage and only start when metrics export over OTLP.
 *
 * @param serviceName used as the default OTel service name; OTEL_SERVICE_NAME overrides it. Note that this default
 *   (like any `otel.service.name` value) takes precedence over a `service.name` entry in OTEL_RESOURCE_ATTRIBUTES.
 * @param customize applies application defaults and customizers to the autoconfigure builder before the SDK is built.
 *   Properties it supplies are defaults; environment variables and system properties take precedence over them.
 * @param onInitialized receives the resolved configuration once the SDK is built and published globally. It is not
 *   called when another component already published a global OpenTelemetry instance.
 */
@JvmOverloads
fun initializeOpenTelemetrySdk(
  serviceName: String? = null,
  customize: (AutoConfiguredOpenTelemetrySdkBuilder) -> Unit = {},
  onInitialized: (ConfigProperties) -> Unit = {},
): OpenTelemetry {
  // Fast path after the fully initialized SDK has been safely published.
  initializedSdk.get()?.let {
    return it
  }

  return synchronized(initializationLock) {
    // The SDK can only be registered as the global instance once per process. Recheck after
    // acquiring the lock so concurrent callers return the first fully initialized result.
    initializedSdk.get()?.let {
      return@synchronized it
    }

    // Database semantic-convention selection is cached when OTel Java instrumentation first loads.
    // Configure it before building and globally publishing the SDK.
    configureStableDatabaseSemconv()

    val (otel, options) =
      try {
        val builder = openTelemetrySdkBuilder(serviceName)
        customize(builder)
        val configuredSdk = builder.setResultAsGlobal().build()

        val config = AutoConfigureUtil.getConfig(configuredSdk)
        configuredSdk.openTelemetrySdk to
          OpenTelemetryOptions(
            metricsExporting =
              config?.getBoolean("otel.sdk.disabled", false) == false &&
                isOtlpMetricsExporterSelected(
                  config.getString(METRICS_EXPORTER_PROPERTY)?.takeIf { it.isNotBlank() } ?: "otlp"
                ),
            emitExperimentalJfrMetrics =
              config?.getBoolean("otel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics", false) == true,
            config = config,
          )
      } catch (e: IllegalStateException) {
        // GlobalOpenTelemetry.set was already called by something OTHER than this function. That
        // usually means something called GlobalOpenTelemetry.get() before this ran, permanently
        // latching the global instance to a no-op — in which case no metrics will be exported. The
        // exception's cause points at the call site that set the global instance first.
        logger.warn(e) { "OpenTelemetry global instance already set; using it as-is" }
        GlobalOpenTelemetry.get() to OpenTelemetryOptions()
      }

    options.config?.let(onInitialized)

    val runtimeMetricsBuilder = RuntimeTelemetry.builder(otel)
    // JFR recording costs memory and CPU even with a no-op meter, so only start it when metrics export.
    if (options.metricsExporting && options.emitExperimentalJfrMetrics) {
      // Add JFR-only metrics without replacing the stable MXBean-backed runtime metrics.
      Experimental.setEmitExperimentalJfrMetrics(runtimeMetricsBuilder, true)
    }
    val runtimeMetrics = runtimeMetricsBuilder.build()
    Runtime.getRuntime().addShutdownHook(Thread { runtimeMetrics.close() })
    initializedSdk.set(otel)
    otel
  }
}
