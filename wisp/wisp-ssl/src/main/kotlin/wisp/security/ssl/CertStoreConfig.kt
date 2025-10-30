package wisp.security.ssl

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "CertStoreConfig()",
    imports = ["misk.security.ssl.CertStoreConfig"]
  )
)
data class CertStoreConfig @JvmOverloads constructor(
    val resource: String,
    val passphrase: String? = null,
    val format: String = SslLoader.FORMAT_JCEKS
)
