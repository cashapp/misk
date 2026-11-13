package misk.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.common.ComponentLoader
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.common.export.GrpcResponse
import io.opentelemetry.sdk.common.export.GrpcSender
import io.opentelemetry.sdk.common.export.GrpcSenderProvider
import io.opentelemetry.sdk.common.export.GrpcStatusCode
import io.opentelemetry.sdk.common.export.HttpResponse
import io.opentelemetry.sdk.common.export.HttpSender
import io.opentelemetry.sdk.common.export.HttpSenderProvider
import io.opentelemetry.sdk.common.export.MessageWriter
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData
import io.opentelemetry.sdk.resources.Resource
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class OpenTelemetryInitializationTest {
  private val exporters = mutableListOf<MetricExporter>()

  @AfterEach
  fun shutDownExporters() {
    exporters.forEach(MetricExporter::shutdown)
  }

  @Test
  fun `stable database semantic conventions are enabled by default`() {
    assertThat(stableDatabaseSemconvOptIns(null)).isEqualTo("database")
    assertThat(stableDatabaseSemconvOptIns("")).isEqualTo("database")
  }

  @Test
  fun `stable database semantic conventions preserve other opt-ins`() {
    assertThat(stableDatabaseSemconvOptIns("code,rpc")).isEqualTo("code,rpc,database")
  }

  @Test
  fun `stable database semantic conventions replace database dual emission`() {
    assertThat(stableDatabaseSemconvOptIns(" database/dup, code, database,code ")).isEqualTo("code,database")
  }

  @Test
  fun `bootstrap selects stable database conventions before instrumentation initializes`() {
    val semconvProperty = "otel.semconv-stability.opt-in"
    val sdkDisabledProperty = "otel.sdk.disabled"
    val previousSemconvOptIns = System.getProperty(semconvProperty)
    val previousSdkDisabled = System.getProperty(sdkDisabledProperty)
    System.setProperty(semconvProperty, "code,database/dup")
    System.setProperty(sdkDisabledProperty, "true")

    try {
      initializeOpenTelemetrySdk(serviceName = "semconv-bootstrap-test")

      assertThat(System.getProperty(semconvProperty)).isEqualTo("code,database")
      val semconvStability = Class.forName("io.opentelemetry.instrumentation.api.internal.SemconvStability")
      assertThat(semconvStability.getMethod("emitStableDatabaseSemconv").invoke(null)).isEqualTo(true)
      assertThat(semconvStability.getMethod("emitOldDatabaseSemconv").invoke(null)).isEqualTo(false)
    } finally {
      if (previousSemconvOptIns == null) {
        System.clearProperty(semconvProperty)
      } else {
        System.setProperty(semconvProperty, previousSemconvOptIns)
      }
      if (previousSdkDisabled == null) {
        System.clearProperty(sdkDisabledProperty)
      } else {
        System.setProperty(sdkDisabledProperty, previousSdkDisabled)
      }
    }
  }

  @Test
  fun `memory mode environment property maps to exporter property`() {
    val config = StubConfigProperties(strings = mapOf("otel.java.exporter.memory.mode" to "immutable_data"))

    assertThat(exporterMemoryModeOverride(config))
      .containsExactlyEntriesOf(mapOf("otel.java.exporter.memory_mode" to "immutable_data"))
  }

  @Test
  fun `empty memory mode environment property remains unset`() {
    val config = StubConfigProperties(strings = mapOf("otel.java.exporter.memory.mode" to ""))

    assertThat(exporterMemoryModeOverride(config)).isEmpty()
  }

  @Test
  fun `memory mode exporter property takes precedence over normalized environment property`() {
    val config =
      StubConfigProperties(
        strings =
          mapOf(
            "otel.java.exporter.memory_mode" to "reusable_data",
            "otel.java.exporter.memory.mode" to "immutable_data",
          )
      )

    assertThat(exporterMemoryModeOverride(config)).isEmpty()
  }

  @Test
  fun `empty memory mode exporter property still takes precedence`() {
    val config =
      StubConfigProperties(
        strings = mapOf("otel.java.exporter.memory_mode" to "", "otel.java.exporter.memory.mode" to "immutable_data")
      )

    assertThat(exporterMemoryModeOverride(config)).isEmpty()
  }

  @Test
  fun `OTLP gRPC exporter logger class is present`() {
    val exporterClass = Class.forName(OTLP_GRPC_EXPORTER_LOGGER, false, javaClass.classLoader)

    assertThat(exporterClass.name).isEqualTo(OTLP_GRPC_EXPORTER_LOGGER)
  }

  @Test
  fun `metric exporter failure log matches export failure filter`() {
    val reset = StreamResetException(ErrorCode.CANCEL)
    val record =
      exportFailureLog(
        object : GrpcSender {
          override fun send(writer: MessageWriter, onResponse: Consumer<GrpcResponse>, onError: Consumer<Throwable>) {
            onError.accept(reset)
          }

          override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }
      )

    assertThat(record.loggerName).isEqualTo(OTLP_GRPC_EXPORTER_LOGGER)
    assertThat(record.thrown).isSameAs(reset)
    assertThat(isOtlpMetricExportFailure(record.loggerName, record.message, record.thrown)).isTrue()
  }

  @Test
  fun `metric exporter unavailable log matches export failure filter`() {
    val record =
      exportFailureLog(
        object : GrpcSender {
          override fun send(writer: MessageWriter, onResponse: Consumer<GrpcResponse>, onError: Consumer<Throwable>) {
            onResponse.accept(
              object : GrpcResponse {
                override fun getStatusCode() = GrpcStatusCode.UNAVAILABLE

                override fun getStatusDescription() = "no healthy upstream"

                override fun getResponseMessage() = ByteArray(0)
              }
            )
          }

          override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }
      )

    assertThat(record.loggerName).isEqualTo(OTLP_GRPC_EXPORTER_LOGGER)
    assertThat(record.message).contains("Server is UNAVAILABLE")
    assertThat(record.thrown).isNull()
    assertThat(isOtlpMetricExportFailure(record.loggerName, record.message, record.thrown)).isTrue()
  }

  @Test
  fun `metric exporter call timeout log matches export failure filter`() {
    val timeout = InterruptedIOException("timeout").apply { initCause(IOException("Canceled")) }
    val record =
      exportFailureLog(
        object : GrpcSender {
          override fun send(writer: MessageWriter, onResponse: Consumer<GrpcResponse>, onError: Consumer<Throwable>) {
            onError.accept(timeout)
          }

          override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }
      )

    assertThat(record.loggerName).isEqualTo(OTLP_GRPC_EXPORTER_LOGGER)
    assertThat(record.thrown).isSameAs(timeout)
    assertThat(isOtlpMetricExportFailure(record.loggerName, record.message, record.thrown)).isTrue()
  }

  @Test
  fun `OTLP HTTP exporter logger class is present`() {
    val exporterClass = Class.forName(OTLP_HTTP_EXPORTER_LOGGER, false, javaClass.classLoader)

    assertThat(exporterClass.name).isEqualTo(OTLP_HTTP_EXPORTER_LOGGER)
  }

  @Test
  fun `HTTP metric exporter error response log matches export failure filter`() {
    val record =
      httpExportFailureLog(
        object : HttpSender {
          override fun send(writer: MessageWriter, onResponse: Consumer<HttpResponse>, onError: Consumer<Throwable>) {
            onResponse.accept(
              object : HttpResponse {
                override fun getStatusCode() = 503

                override fun getStatusMessage() = "Service Unavailable"

                override fun getResponseBody() = ByteArray(0)
              }
            )
          }

          override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }
      )

    assertThat(record.loggerName).isEqualTo(OTLP_HTTP_EXPORTER_LOGGER)
    assertThat(record.message).contains("HTTP status code 503")
    assertThat(record.thrown).isNull()
    assertThat(isOtlpMetricExportFailure(record.loggerName, record.message, record.thrown)).isTrue()
  }

  @Test
  fun `HTTP metric exporter transport failure log matches export failure filter`() {
    val failure = IOException("connection refused")
    val record =
      httpExportFailureLog(
        object : HttpSender {
          override fun send(writer: MessageWriter, onResponse: Consumer<HttpResponse>, onError: Consumer<Throwable>) {
            onError.accept(failure)
          }

          override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
        }
      )

    assertThat(record.loggerName).isEqualTo(OTLP_HTTP_EXPORTER_LOGGER)
    assertThat(record.thrown).isSameAs(failure)
    assertThat(isOtlpMetricExportFailure(record.loggerName, record.message, record.thrown)).isTrue()
  }

  @Test
  fun `OTLP metric export failure matches exporter logger and metric signal`() {
    assertThat(
        isOtlpMetricExportFailure(
          "io.opentelemetry.exporter.otlp.internal.GrpcExporter",
          "Failed to export metrics. Server is UNAVAILABLE. Full error message:no healthy upstream",
          null,
        )
      )
      .isTrue()
    assertThat(
        isOtlpMetricExportFailure(
          "io.opentelemetry.exporter.otlp.internal.GrpcExporter",
          "Failed to export 12 metrics. Server responded with UNIMPLEMENTED.",
          null,
        )
      )
      .isTrue()
    assertThat(
        isOtlpMetricExportFailure(
          "io.opentelemetry.exporter.otlp.internal.GrpcExporter",
          "Failed to export 1 metrics. The request could not be executed.",
          IllegalStateException("any exporter failure"),
        )
      )
      .isTrue()
    assertThat(
        isOtlpMetricExportFailure(
          "io.opentelemetry.exporter.otlp.internal.GrpcExporter",
          "Failed to export 1 spans. The request could not be executed.",
          IllegalStateException("span failure"),
        )
      )
      .isFalse()
    assertThat(
        isOtlpMetricExportFailure(
          "io.opentelemetry.exporter.otlp.internal.GrpcExporter",
          "Failed to export 1 spans. Server is UNAVAILABLE. Full error message:upstream metrics.",
          IllegalStateException("span failure"),
        )
      )
      .isFalse()
    assertThat(
        isOtlpMetricExportFailure(
          "other.logger",
          "Failed to export 1 metrics. The request could not be executed.",
          IllegalStateException("unrelated logger"),
        )
      )
      .isFalse()
    assertThat(
        isOtlpMetricExportFailure(
          "io.opentelemetry.exporter.otlp.internal.GrpcExporter",
          "Unrelated exporter message",
          null,
        )
      )
      .isFalse()
  }

  private fun exportFailureLog(sender: GrpcSender): LogRecord =
    exportFailureLog(
      OTLP_GRPC_EXPORTER_LOGGER,
      Level.SEVERE,
      GrpcSenderProvider::class.java,
      GrpcSenderProvider { sender },
    ) {
      OtlpGrpcMetricExporter.builder().setComponentLoader(it).setRetryPolicy(null).build()
    }

  private fun httpExportFailureLog(sender: HttpSender): LogRecord =
    exportFailureLog(
      OTLP_HTTP_EXPORTER_LOGGER,
      Level.WARNING,
      HttpSenderProvider::class.java,
      HttpSenderProvider { sender },
    ) {
      OtlpHttpMetricExporter.builder().setComponentLoader(it).setRetryPolicy(null).build()
    }

  private fun <P : Any> exportFailureLog(
    loggerName: String,
    minimumLevel: Level,
    providerType: Class<P>,
    provider: P,
    exporterFactory: (ComponentLoader) -> MetricExporter,
  ): LogRecord {
    val exporterLogger = Logger.getLogger(loggerName)
    val records = mutableListOf<LogRecord>()
    val handler =
      object : Handler() {
        override fun publish(record: LogRecord) {
          if (isLoggable(record)) records += record
        }

        override fun flush() = Unit

        override fun close() = Unit
      }
    val previousLevel = exporterLogger.level
    val previousUseParentHandlers = exporterLogger.useParentHandlers

    return try {
      exporterLogger.level = minimumLevel
      exporterLogger.useParentHandlers = false
      handler.level = minimumLevel
      exporterLogger.addHandler(handler)

      val componentLoader =
        object : ComponentLoader {
          override fun <T : Any?> load(spiClass: Class<T>): Iterable<T> =
            if (spiClass == providerType) listOf(spiClass.cast(provider)) else emptyList()
        }
      val exporter = track(exporterFactory(componentLoader))

      val result = exporter.export(listOf(metricData()))
      result.join(5, TimeUnit.SECONDS)

      assertThat(result.isDone).isTrue()
      assertThat(result.isSuccess).isFalse()
      assertThat(records).hasSize(1)
      records.single()
    } finally {
      exporterLogger.removeHandler(handler)
      exporterLogger.useParentHandlers = previousUseParentHandlers
      exporterLogger.level = previousLevel
    }
  }

  private fun <T : MetricExporter> track(exporter: T): T {
    exporters += exporter
    return exporter
  }

  private fun metricData(): MetricData =
    ImmutableMetricData.createLongSum(
      Resource.empty(),
      InstrumentationScopeInfo.create("test"),
      "counter",
      "deterministic counter",
      "1",
      ImmutableSumData.create(
        true,
        AggregationTemporality.CUMULATIVE,
        listOf(ImmutableLongPointData.create(1_000L, 2_000L, Attributes.empty(), 5L)),
      ),
    )
}

/**
 * Minimal [ConfigProperties] for the customizer tests. [malformedDurations] mimics autoconfigure's behavior of throwing
 * [ConfigurationException] from [getDuration] when the configured value doesn't parse.
 */
private class StubConfigProperties(
  private val retryDisabled: Boolean? = null,
  private val strings: Map<String, String> = emptyMap(),
  private val ints: Map<String, Int> = emptyMap(),
  private val durations: Map<String, Duration> = emptyMap(),
  private val malformedDurations: Boolean = false,
) : ConfigProperties {
  override fun getString(name: String): String? = strings[name]

  override fun getBoolean(name: String): Boolean? =
    if (name == "otel.java.exporter.otlp.retry.disabled") retryDisabled else null

  override fun getInt(name: String): Int? = ints[name]

  override fun getLong(name: String): Long? = null

  override fun getDouble(name: String): Double? = null

  override fun getDuration(name: String): Duration? {
    if (malformedDurations) throw ConfigurationException("Invalid duration property $name=malformed")
    return durations[name]
  }

  override fun getList(name: String): List<String> = emptyList()

  override fun getMap(name: String): Map<String, String> = emptyMap()
}
