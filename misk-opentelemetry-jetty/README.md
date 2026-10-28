# misk-opentelemetry-jetty

OpenTelemetry metrics for Misk's Jetty server: handler statistics, thread pools, upstream Jetty JMX
rules, and connections. Bind the SDK with [`misk-opentelemetry`](../misk-opentelemetry/README.md)
first, then install:

```kotlin
MiskApplication(
  // ...
  OpenTelemetryModule(openTelemetry),
  JettyOpenTelemetryModule(jettyEnabled = !webConfig.disable_jetty),
).run(args)
```

`JettyOpenTelemetryModule` installs Jetty observations only when Jetty is enabled and the
process-wide SDK exports metrics: the SDK is enabled (unless `OTEL_SDK_DISABLED=true`, matching
OpenTelemetry autoconfigure) and `OTEL_METRICS_EXPORTER` (or `otel.metrics.exporter`) selects
`otlp`, which is autoconfigure's default when unset. Pass `metricsExporting` to apply a different
rule. Without export, no connection poller or Jetty JMX collection starts. The upstream Jetty
JMX rules run **in-process** against the application's SDK: no javaagent, remote JMX endpoint,
second SDK, or exporter is installed.

An `MBeanContainer` is attached to Misk's already-running Jetty server. The container registers
existing and future managed beans in a private local MBeanServer; both the collector and the
container close and unregister when the metrics service stops. JMX instruments carry the
`io.opentelemetry.jmx` scope; handler and thread-pool observations use `misk.jetty`; connection
metrics use the service's `@AppName` meter.

The upstream Jetty rules provide `jetty.thread.count`, `jetty.thread.limit`,
`jetty.thread.busy.count`, `jetty.thread.idle.count`, `jetty.thread.queue.size`,
`jetty.select.count`, and session metrics. Session handlers yield `jetty.session.created.count`
and `jetty.session.duration.sum` (seconds), carrying the `jetty.context` attribute from Jetty's
JMX ObjectName (`/orders` becomes `orders`). Contexts without session handlers have no session
series. Misk normally supplies a `QueuedThreadPool`, whose JMX MBean matches the upstream Jetty
thread rules. If configured with `jetty_max_thread_pool_queue_size <= 0`, Misk instead supplies an
`ExecutorThreadPool`, which has no upstream `QueuedThreadPool` MBean: only in this mode are native
thread observations registered under `misk.jetty`. Native and JMX thread callbacks are never
registered together for the same pool.

The existing Prometheus StatisticsHandler, thread-pool, and connection collectors are unchanged.
The `misk.jetty` meter observes requests, dispatches, response classes and bytes, statistics
uptime, and servlet async requests, errors, and thrown responses. Cumulative handler values are
observed as counters, not re-added at each collection.

A connector listener records connection lifetime, active connections (an UpDownCounter, so
connectors sum), transport bytes and messages under `jetty.connection.*`, plus accepted
connections as `jetty.connections.accepted`. These count sockets, not Jetty `Connection` objects:
TLS stacks an `SslConnection` beneath the HTTP connection, ALPN hands off to HTTP/1.1 or HTTP/2,
and cleartext HTTP/2 upgrades from HTTP/1.1, yet each socket is accepted and timed once. Bytes are
transport bytes (encrypted under TLS). In Jetty 11 only HTTP/1.1 counts messages, and HTTP/2
counts neither bytes nor messages, so a cleartext HTTP/2 socket reports bytes only until it
upgrades. OpenTelemetry semantic conventions define no HTTP
server connection metrics, so none of these use the `http.server.*` namespace. Only the main
Jetty server is observed, and a connector named `health` is skipped, so health-check connections
are excluded; Misk's legacy `http_connections*` metrics include them. Every Unix domain socket is
observed by default and labeled with its path as `network.local.address`; pass
`observedUnixDomainSockets` to observe only some. Labels are bounded to
`jetty.response.status_class`, connector protocol/port, and the configured socket paths.
Connections still open when the observer stops report their elapsed
lifetime and final byte/message totals before Jetty shuts down.

Handler totals belong to the process-wide SDK rather than to one Jetty server: a hot reload
builds a new server whose statistics start at zero, and the new observer continues from the
previous totals instead of exporting negative deltas. Upstream JMX counters such as
`jetty.select.count` still start over with the new server.

Rollback: restart with `OTEL_SDK_DISABLED=true` (or `OTEL_METRICS_EXPORTER=none`) to stop OTel
export without changing Prometheus collectors.
