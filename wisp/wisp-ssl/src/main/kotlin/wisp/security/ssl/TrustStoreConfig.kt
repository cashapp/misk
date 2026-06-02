package wisp.security.ssl

import wisp.security.ssl.SslLoader.Companion.FORMAT_JCEKS

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(
      expression = "TrustStoreConfig(resource, passphrase, format)",
      imports = ["misk.security.ssl.TrustStoreConfig"],
    ),
)
data class TrustStoreConfig
@JvmOverloads
constructor(val resource: String, val passphrase: String? = null, val format: String = FORMAT_JCEKS)
