package misk.security.ssl

import misk.security.ssl.SslLoader.Companion.FORMAT_JCEKS

data class TrustStoreConfig(
  val path: String,
  val passphrase: String? = null,
  val format: String = FORMAT_JCEKS
)
