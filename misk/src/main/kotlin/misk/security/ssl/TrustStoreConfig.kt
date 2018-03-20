package misk.security.ssl

data class TrustStoreConfig(
  val path: String,
  val passphrase: String? = null,
  val type: String = Keystores.TYPE_JCEKS
) {
  fun load() = Keystores.loadTrustStore(path, type, passphrase)
}
