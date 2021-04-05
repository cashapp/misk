package misk.security.ssl

import misk.resources.ResourceLoader
import javax.inject.Inject
import wisp.security.ssl.SslLoader as WispSslLoader

/** Loads keys and certificates from the file system. */
class SslLoader @Inject internal constructor(
  val resourceLoader: ResourceLoader
) {
  val delegate: WispSslLoader = WispSslLoader(resourceLoader.delegate)

  fun loadTrustStore(
    path: String,
    format: String = FORMAT_PEM,
    passphrase: String? = null
  ): TrustStore? = delegate.loadTrustStore(path, format, passphrase)

  fun loadTrustStore(config: TrustStoreConfig) = delegate.loadTrustStore(config.toWispConfig())

  fun loadCertStore(
    path: String,
    format: String = FORMAT_PEM,
    passphrase: String? = null
  ): CertStore? = delegate.loadCertStore(path, format, passphrase)

  fun loadCertStore(config: CertStoreConfig) = delegate.loadCertStore(config.toWispConfig())

  companion object {
    const val FORMAT_PEM = "PEM"
    const val FORMAT_JCEKS = "JCEKS"
    const val FORMAT_JKS = "JKS"
  }
}
