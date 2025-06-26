package misk.security.ssl

import misk.config.Redact
import jakarta.inject.Inject
import wisp.security.ssl.CertStoreConfig as WispCertStoreConfig

data class CertStoreConfig @Inject constructor(
  val resource: String,
  @Redact
  val passphrase: String? = null,
  val format: String = SslLoader.FORMAT_JCEKS
) {
  @Deprecated("Duplicate implementations in Wisp are being migrated to the unified type in Misk.")
  fun toWispConfig() = WispCertStoreConfig(resource, passphrase, format)
}
