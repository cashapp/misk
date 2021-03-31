package misk.security.ssl

import misk.security.ssl.SslLoader.Companion.FORMAT_JCEKS
import javax.inject.Inject
import wisp.security.ssl.TrustStoreConfig as WispTrustStoreConfig

data class TrustStoreConfig @Inject constructor(
  val resource: String,
  val passphrase: String? = null,
  val format: String = FORMAT_JCEKS
) {
  fun toWispConfig() = WispTrustStoreConfig(resource, passphrase, format)
}
