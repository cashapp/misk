package wisp.security.ssl

data class CertStoreConfig constructor(
  val resource: String,
  val passphrase: String? = null,
  val format: String = SslLoader.FORMAT_JCEKS
)
