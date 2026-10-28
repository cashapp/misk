# misk-opentelemetry

- When upgrading OpenTelemetry dependencies, inspect the pinned release's `GrpcExporter` implementation and verify
  that its logger and every metric failure message still satisfy `isOtlpMetricExportFailure`. Cover response failures
  without throwables and transport failures with arbitrary throwable types; do not classify exporter failures by
  exception type or stack frame. Update the filter, version comment, and exporter-log contract tests together if
  upstream logging changes.
