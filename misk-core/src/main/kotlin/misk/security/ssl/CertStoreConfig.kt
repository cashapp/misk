package misk.security.ssl

import javax.inject.Inject

data class CertStoreConfig @Inject constructor(
  val resource: String,
  val passphrase: String? = null,
  val format: String = SslLoader.FORMAT_JCEKS
)
