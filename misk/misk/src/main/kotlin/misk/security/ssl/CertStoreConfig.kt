package misk.security.ssl

data class CertStoreConfig(
  val resource: String,
  val passphrase: String? = null,
  val format: String = SslLoader.FORMAT_JCEKS
)
