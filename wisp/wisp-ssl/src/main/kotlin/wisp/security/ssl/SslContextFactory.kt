package wisp.security.ssl

import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "SslContextFactory(sslLoader)",
    imports = ["misk.security.ssl.SslContextFactory"]
  )
)
open class SslContextFactory constructor(private val sslLoader: SslLoader) {
    /** @return A new [SSLContext] for the given certstore and optional truststore config */
    @JvmOverloads
    fun create(certStore: CertStoreConfig? = null, trustStore: TrustStoreConfig? = null): SSLContext {
        val loadedCertStore = certStore?.let { sslLoader.loadCertStore(certStore) }
        val loadedTrustStore = trustStore?.let { sslLoader.loadTrustStore(trustStore) }
        return create(loadedCertStore, certStore?.passphrase?.toCharArray(), loadedTrustStore)
    }

    /** @return A new [SSLContext] for the given certstore and optional truststore config */
    @JvmOverloads
    fun create(certStore: CertStore?, pin: CharArray?, trustStore: TrustStore? = null): SSLContext {
        val sslContext = SSLContext.getInstance("TLS", "SunJSSE")
        val trustManagers = trustStore?.keyStore?.let {
            loadTrustManagers(it)
        } ?: arrayOf()
        val keyManagers = certStore?.keyStore?.let {
            arrayOf(KeyStoreX509KeyManager(pin!!, it))
        } ?: arrayOf()
        sslContext.init(keyManagers, trustManagers, null)
        return sslContext
    }

    /** @return a set of [TrustManager]s based on the certificates in the given truststore */
    fun loadTrustManagers(trustStore: KeyStore): Array<TrustManager> {
        val trustManagerFactory = TrustManagerFactory.getInstance(trustAlgorithm)
        trustManagerFactory.init(trustStore)
        return trustManagerFactory.trustManagers
    }

    private val trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
}
