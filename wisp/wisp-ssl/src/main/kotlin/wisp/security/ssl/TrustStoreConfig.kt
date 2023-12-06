package wisp.security.ssl

import wisp.security.ssl.SslLoader.Companion.FORMAT_JCEKS

data class TrustStoreConfig @JvmOverloads constructor(
    val resource: String,
    val passphrase: String? = null,
    val format: String = FORMAT_JCEKS
)
