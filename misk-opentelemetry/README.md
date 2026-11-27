# misk-opentelemetry

OpenTelemetry SDK setup for Misk services. Jetty server, thread-pool, and connection metrics live
in [`misk-opentelemetry-jetty`](../misk-opentelemetry-jetty/README.md).

## Setup

Initialize the SDK once, before building the injector, and bind it:

```kotlin
val openTelemetry = initializeOpenTelemetrySdk(serviceName = appName)

MiskApplication(
  // ...
  OpenTelemetryModule(openTelemetry),
).run(args)
```

`initializeOpenTelemetrySdk` uses OpenTelemetry autoconfigure, so standard `OTEL_*` environment
variables and `otel.*` system properties apply with autoconfigure's defaults. On top of
autoconfigure it:

- applies `OTEL_JAVA_EXPORTER_MEMORY_MODE` from the environment (autoconfigure renames the
  property, so the exporter would otherwise ignore it);
- opts database instrumentation into stable semantic conventions unless
  `OTEL_SEMCONV_STABILITY_OPT_IN` already selects a database mode (such as `database/dup` while
  dashboards migrate), preserving other selections;
- registers JVM runtime metrics. Set
  `OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_JFR_METRICS=true` to add JFR-based
  metrics; they cost memory and only start when metrics export;
- replaces autoconfigure's shutdown hook with one that waits up to 60 seconds for Misk
  applications binding `OpenTelemetryModule` to stop before closing the SDK, so metrics recorded
  while services drain are still exported.

Pass `customize` to supply application defaults or customizers on the autoconfigure builder, and
`onInitialized` to act on the resolved configuration once the SDK is published:

```kotlin
initializeOpenTelemetrySdk(
  serviceName = appName,
  customize = { builder ->
    builder.addPropertiesSupplier {
      mapOf("otel.traces.exporter" to "none", "otel.metric.export.interval" to "10000")
    }
  },
)
```

Supplied properties are defaults; environment variables and system properties override them.

To build an additional, non-global SDK configured the same way (for example, one per in-process
application reload), start from `openTelemetrySdkBuilder(serviceName)`, apply the same
customizations, and call `build()` without `setResultAsGlobal()`; close it yourself.

`openTelemetryMetricsExporting()` reports whether the process SDK exports metrics (enabled, and
an exporter other than `none` selected), using its resolved configuration once it is built.

OTLP metric export failures are noisy during collector rollouts. `isOtlpMetricExportFailure`
classifies the exporter's failure log records so a logging setup can demote them to WARN or keep
them out of error reporting. It also matches the exporters' "Too many log messages detected"
throttling notice, which does not name a signal.
