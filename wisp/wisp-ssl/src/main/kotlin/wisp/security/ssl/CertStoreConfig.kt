package wisp.security.ssl

data class CertStoreConfig @JvmOverloads constructor(
    val resource: String,
    val passphrase: String? = null,
    val format: String = SslLoader.FORMAT_JCEKS
)
