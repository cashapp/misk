package misk.cloud.gcp

/** Transport configuration for GCP services. */
data class TransportConfig(
    val connect_timeout_ms: Int = -1,
    val read_timeout_ms: Int = -1,
    val host: String? = null
)
