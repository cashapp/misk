package misk.security.ssl

import java.security.KeyStore
import jakarta.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import wisp.security.ssl.SslContextFactory as WispSslContextFactory

class SslContextFactory @Inject constructor(private val sslLoader: SslLoader) {
  val delegate: WispSslContextFactory = WispSslContextFactory(sslLoader.delegate)

  /** @return A new [SSLContext] for the given certstore and optional truststore config */
  @JvmOverloads
  fun create(certStore: CertStoreConfig? = null, trustStore: TrustStoreConfig? = null): SSLContext =
    delegate.create(certStore?.toWispConfig(), trustStore?.toWispConfig())

  /** @return A new [SSLContext] for the given certstore and optional truststore config */
  @JvmOverloads
  fun create(certStore: CertStore?, pin: CharArray?, trustStore: TrustStore? = null): SSLContext =
    delegate.create(certStore?.let { wisp.security.ssl.CertStore(it.keyStore) }, pin, trustStore?.let { wisp.security.ssl.TrustStore(it.keyStore) })

  /** @return a set of [TrustManager]s based on the certificates in the given truststore */
  fun loadTrustManagers(trustStore: KeyStore): Array<TrustManager> =
    delegate.loadTrustManagers(trustStore)
}
