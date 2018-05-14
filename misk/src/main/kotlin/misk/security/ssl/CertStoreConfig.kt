package misk.security.ssl

data class CertStoreConfig(
  val path: String,
  val passphrase: String? = null,
  val type: String = Keystores.TYPE_JCEKS
) {
  fun load() = Keystores.loadCertStore(path, type, passphrase)
}
