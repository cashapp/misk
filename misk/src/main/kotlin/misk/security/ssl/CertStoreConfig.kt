package misk.security.ssl

data class CertStoreConfig(
  val path: String,
  val passphrase: String? = null,
  val format: String = SslLoader.FORMAT_JCEKS
)
