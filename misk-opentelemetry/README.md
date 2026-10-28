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
- always opts database instrumentation into stable semantic conventions, preserving other
  `OTEL_SEMCONV_STABILITY_OPT_IN` selections;
- registers JVM runtime metrics. Set
  `OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_JFR_METRICS=true` to add JFR-based
  metrics; they cost memory and only start when metrics export over OTLP.

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
customizations, and call `build()` without `setResultAsGlobal()`.

OTLP metric export failures are noisy during collector rollouts. `isOtlpMetricExportFailure`
classifies the exporter's failure log records so a logging setup can demote them to WARN or keep
them out of error reporting.
