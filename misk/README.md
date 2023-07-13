# Module: Misk Web Module
**[Module Documentation Pending]**

## Metrics
### Network - Inbound
This module automatically generates metrics for inbound network calls. These metrics are defined by the `MetricsInterceptor` class, and are as follows:

| Metric                    | Slug                          | Description                                       | Labels                    | Type                                                                                      |
|---------------------------| ----------------------------- | ------------------------------------------------- | ------------------------- | ----------------------------------------------------------------------------------------- |
| Request Latency Summary   | http_request_latency_ms       | count and duration in ms of incoming web requests | Action,Caller,Status Code | [Summary](https://prometheus.github.io/client_java/io/prometheus/client/Summary.html)     |
| Request Latency Histogram | histo_http_request_latency_ms | count and duration in ms of incoming web requests | Action,Caller,Status Code | [Histogram](https://prometheus.github.io/client_java/io/prometheus/client/Histogram.html) |

### Network - Outbound
This module automatically generates metrics for outbound network calls. These metrics are defined by the `ClientMetricsInterceptor` class, and are as follows:

| Metric                    | Slug                                 | Description                                          | Labels              | Type                                                                                      |
|---------------------------| ------------------------------------ | ---------------------------------------------------- | ------------------- | ----------------------------------------------------------------------------------------- |
| Request Latency Summary   | client_http_request_latency_ms       | count and duration in ms of outgoing client requests | Action, Status Code | [Summary](https://prometheus.github.io/client_java/io/prometheus/client/Summary.html)     |
| Request Latency Histogram | histo_client_http_request_latency_ms | histogram in ms of outgoing client requests          | Action, Status Code | [Histogram](https://prometheus.github.io/client_java/io/prometheus/client/Histogram.html) |

*Note:* Not every client is compatible with this module. For more details check `GrpcClientProvider`.

### Network - Jetty
This module automatically generates metrics for Jetty Web Server. These metrics are defined by the `ConnectionMetrics` class, and are as follows:

| Metric              | Slug                        | Description                                                       | Labels         | Type                                                                                      |
| ------------------- | --------------------------- | ----------------------------------------------------------------- | -------------- | ----------------------------------------------------------------------------------------- |
| Total Connections   | http_connections_total      | total number of connections accepted by jetty                     | Protocol, Port | [Counter](https://prometheus.github.io/client_java/io/prometheus/client/Counter.html)     |
| Connection Duration | http_connection_duration_ms | average duration a incoming jetty connection is held open (in ms) | Protocol, Port | [Histogram](https://prometheus.github.io/client_java/io/prometheus/client/Histogram.html) |
| Active Connections  | http_connections_active     | number of currently active connections in jetty                   | Protocol, Port | [Gauge](https://prometheus.github.io/client_java/io/prometheus/client/Gauge.html)         |
| Bytes Received      | http_bytes_recvd_total      | total count of bytes received by jetty                            | Protocol, Port | [Counter](https://prometheus.github.io/client_java/io/prometheus/client/Counter.html)     |
| Bytes Sent          | http_bytes_sent_total       | total count of bytes sent through jetty                           | Protocol, Port | [Counter](https://prometheus.github.io/client_java/io/prometheus/client/Counter.html)     |
| Messages Received   | http_msgs_recvd_total       | total count of HTTP messages received by jetty                    | Protocol, Port | [Counter](https://prometheus.github.io/client_java/io/prometheus/client/Counter.html)     |
| Messages Sent       | http_msgs_sent_total        | total count of HTTP messages sent by jetty                        | Protocol, Port | [Counter](https://prometheus.github.io/client_java/io/prometheus/client/Counter.html)     |


# SubModule: Monitoring Module
**[SubModule Documentation Pending]**

## Metrics
### JVM
This submodule automatically generates jvm-related metrics. These metrics are defined by the `JVMMetrics` class, and are as follows:

| Metric             | Slug          | Description                | Labels | Type                                                                              |
|--------------------| ------------- | -------------------------- | ------ | --------------------------------------------------------------------------------- |
| JVM Uptime Summary | jvm_uptime_ms | JVM uptime in milliseconds |        | [Gauge](https://prometheus.github.io/client_java/io/prometheus/client/Gauge.html) |

# Submodule: Pause Detector Module
**[SubModule Documentation Pending]**

## Metrics
### JVM Hiccups
This submodule automatically generates jvm-hiccup metrics. These metrics are defined by the `PauseDetector` class, and are as follows:

| Metric               | Slug                      | Description                 | Labels | Type                                                                                                              |
| -------------------- | ------------------------- | --------------------------- | ------ | ----------------------------------------------------------------------------------------------------------------- |
| JVM "hiccup" summary | jvm_pause_time_summary_ms | Summary in ms of pause time |        | [Summary](https://prometheus.github.io/client_java/io/prometheus/client/Summary.html)                             |
| JVM "hiccup" peak    | jvm_pause_time_peak_ms    | Peak gauge of pause time    |        | [Gauge](https://prometheus.github.io/client_java/io/prometheus/client/Gauge.html) (with reset time to show peaks) |

