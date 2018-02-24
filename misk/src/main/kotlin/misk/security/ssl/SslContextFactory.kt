package misk.security.ssl

import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

object SslContextFactory {
    /** @return A new [SSLContext] for the given keystore and optional truststore config */
    fun create(keystore: KeystoreConfig?, truststore: KeystoreConfig? = null) =
            create(keystore?.load(), keystore?.passphrase?.toCharArray(), truststore?.load())

    /** @return A new [SSLContext] for the given keystore and optional truststore config */
    fun create(keystore: KeyStore?, pin: CharArray?, truststore: KeyStore? = null): SSLContext {
        val sslContext = SSLContext.getInstance("TLS", "SunJSSE")
        val trustManagers = truststore
                ?.let {
                    SslContextFactory.loadTrustManagers(it)
                } ?: arrayOf()
        val keyManagers = keystore
                ?.let {
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