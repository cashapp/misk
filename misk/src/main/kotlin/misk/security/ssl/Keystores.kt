package misk.security.ssl

import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

object Keystores {
  const val TYPE_PEM = "PEM"
  const val TYPE_JCEKS = "JCEKS"

  /** Loads a keystore of the provided type from the given stream */
  fun load(input: InputStream, type: String, passphrase: String? = null): KeyStore {
    if (type == TYPE_PEM) return loadPEM(input)

    val keystore = try {
      KeyStore.getInstance(type)
    } catch (e: KeyStoreException) {
      throw IllegalStateException("no provider exists for the keystore type $type", e)
    } catch (e: IllegalAccessException) {
      throw IllegalStateException("no provider exists for the keystore type $type", e)
    } catch (e: ClassNotFoundException) {
      throw IllegalStateException("no provider exists for the keystore type $type", e)
    }

    try {
      input.use { keystore.load(input, passphrase?.toCharArray()) }
    } catch (e: CertificateException) {
      throw IllegalStateException("some certifcates could not be loaded", e)
    } catch (e: NoSuchAlgorithmException) {
      throw IllegalStateException("integrity check algorithm is unavailable", e)
    } catch (e: IOException) {
      throw IllegalArgumentException("I/O error or a bad password", e)
    }

    return keystore
  }

  /**
   * Reads a stream containing PEM certificates into a key store, suitable to initialize a SSL context.
   * The PEM file must contain at least one certificate, and may contain extra data that will be
   * discarded.
   */
  fun loadPEM(input: InputStream): KeyStore {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val certificates = certificateFactory.generateCertificates(input)
    require(certificates.isNotEmpty()) { "expected non-empty set of trusted certificates" }

    // Put the certificates a key store.
    val password = "password".toCharArray() // Any password will work.
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, password) // By convention, null input creates a new empty store

    certificates.forEachIndexed { index, certificate ->
      keyStore.setCertificateEntry(index.toString(), certificate)
    }

    return keyStore
  }
}
