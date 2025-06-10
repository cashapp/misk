package misk.security.ssl

import misk.resources.ResourceLoader
import jakarta.inject.Inject
import wisp.security.ssl.SslLoader as WispSslLoader

/** Loads keys and certificates from the file system. */
class SslLoader @Inject internal constructor(
  val resourceLoader: ResourceLoader
) {
  val delegate: WispSslLoader = WispSslLoader(resourceLoader.delegate)

  @JvmOverloads
  fun loadTrustStore(
    path: String,
    format: String = FORMAT_PEM,
    passphrase: String? = null
  ): TrustStore? = delegate.loadTrustStore(path, format, passphrase)

  fun loadTrustStore(config: TrustStoreConfig) = delegate.loadTrustStore(config.toWispConfig())

  @JvmOverloads
  fun loadCertStore(
    path: String,
    format: String = FORMAT_PEM,
    passphrase: String? = null
  ): CertStore? = delegate.loadCertStore(path, format, passphrase)?.let { CertStore(it.keyStore) }

  fun loadCertStore(config: CertStoreConfig): CertStore? = 
    delegate.loadCertStore(config.toWispConfig())?.let { CertStore(it.keyStore) }

  companion object {
    const val FORMAT_PEM = "PEM"
    const val FORMAT_JCEKS = "JCEKS"
    const val FORMAT_JKS = "JKS"
  }
}
