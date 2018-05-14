package misk.security.ssl

import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException

/** Loads a keystore of the provided type from the given path */
object Keystores {
  const val TYPE_PEM = "PEM"
  const val TYPE_JCEKS = "JCEKS"

  fun loadTrustStore(path: String, type: String, passphrase: String? = null): TrustStore? {
    if (type == TYPE_PEM) return TrustStore.load(path, passphrase)
    return TrustStore.load(loadJavaKeystore(path, type, passphrase))
  }

  fun loadCertStore(path: String, type: String, passphrase: String? = null): CertStore? {
    if (type == TYPE_PEM) return CertStore.load(path, passphrase)
    return CertStore.load(loadJavaKeystore(path, type, passphrase))
  }


  private fun loadJavaKeystore(path: String, type: String, passphrase: String? = null): KeyStore {
    val input = FileInputStream(path)

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
}
