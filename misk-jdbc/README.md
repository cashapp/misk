# Module: JDBC
**[Module Documentation Pending]**


## Metrics
Hikari is a JDBC DataSource implementation that provides a connection pooling mechanism. Hikari is a third-party library, which this module uses under the hood.
This module automatically generates metrics related to JDBC drivers / database connections. These metrics are defined by an [external class](https://sources.debian.org/src/hikaricp/2.7.9-1/src/main/java/com/zaxxer/hikari/metrics/prometheus/PrometheusMetricsTracker.java/) found in Hikari, and are as follows:

| Metric                        | Slug                                | Description                    | Labels               | Type                                                                                  |
|-------------------------------| ----------------------------------- | ------------------------------ | -------------------- | ------------------------------------------------------------------------------------- |
| Connection Pool Timeout       | hikaricp_connection_timeout_total   | Connection timeout total count | Connection Pool Name | [Counter](https://prometheus.github.io/client_java/io/prometheus/client/Counter.html) |
| Connection Acquired Time (ns) | hikaricp_connection_acquired_nanos  | Connection acquired time (ns)  | Connection Pool Name | [Summary](https://prometheus.github.io/client_java/io/prometheus/client/Summary.html) |
| Connection Usage Time (ms)    | hikaricp_connection_usage_millis    | Connection usage (ms)          | Connection Pool Name | [Summary](https://prometheus.github.io/client_java/io/prometheus/client/Summary.html) |
| Connection Creation Time (ms) | hikaricp_connection_creation_millis | Connection creation (ms)       | Connection Pool Name | [Summary](https://prometheus.github.io/client_java/io/prometheus/client/Summary.html) |