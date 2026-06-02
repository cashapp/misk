package misk.security.ssl

import jakarta.inject.Inject
import misk.config.Redact
import misk.security.ssl.SslLoader.Companion.FORMAT_JCEKS
import wisp.security.ssl.TrustStoreConfig as WispTrustStoreConfig

data class TrustStoreConfig
@Inject
constructor(val resource: String, @Redact val passphrase: String? = null, val format: String = FORMAT_JCEKS) {
  @Deprecated("Duplicate implementations in Wisp are being migrated to the unified type in Misk.")
  fun toWispConfig() = WispTrustStoreConfig(resource, passphrase, format)
}
