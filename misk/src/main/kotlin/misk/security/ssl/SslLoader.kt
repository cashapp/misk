package misk.security.ssl

import java.io.FileInputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.spec.PKCS8EncodedKeySpec

/** Loads keys and certificates from the file system. */
class SslLoader {
  fun loadTrustStore(
    path: String,
    format: String = FORMAT_PEM,
    passphrase: String? = null
  ): TrustStore? {
    return when (format) {
      FORMAT_PEM -> {
        // Load a TrustStore from a combined PEM file, returning null in the event that it contains
        // a certificate chain and no private keys.
        PemComboFile.load(path, passphrase).toTrustStore()
      }
      FORMAT_JCEKS -> {
        // TODO(young): This function should check that the underlying keystore complies with what
        // TrustStore provides.
        TrustStore(loadJavaKeystore(path, format, passphrase))
      }
      else -> throw IllegalArgumentException(format)
    }
  }

  fun loadTrustStore(config: TrustStoreConfig) = loadTrustStore(config.path, config.format,
      config.passphrase)

  private fun PemComboFile.toTrustStore(): TrustStore? {
    if (!privateKeys.isEmpty() || !privateRsaKeys.isEmpty()) return null

    val keyStore = newEmptyKeyStore()
    decodeCertificates().forEachIndexed { index, certificate ->
      keyStore.setCertificateEntry(index.toString(), certificate)
    }
    return TrustStore(keyStore)
  }

  fun loadCertStore(
    path: String,
    format: String = FORMAT_PEM,
    passphrase: String? = null
  ): CertStore? {
    return when (format) {
      FORMAT_PEM -> {
        // Load a CertStore from a combined PEM file, returning null in the event that it doesn't
        // contain a single private key and a cert chain.
        PemComboFile.load(path, passphrase).toCertStore()
      }
      FORMAT_JCEKS -> {
        // TODO(young): This function should check that the underlying keystore complies with what
        // CertStore provides.
        CertStore(loadJavaKeystore(path, format, passphrase))
      }
      else -> throw IllegalArgumentException(format)
    }
  }

  fun loadCertStore(config: CertStoreConfig) =
      loadCertStore(config.path, config.format, config.passphrase)

  private fun PemComboFile.toCertStore(): CertStore? {
    if (certificates.isEmpty() || privateRsaKeys.size + privateKeys.size != 1) return null

    val keyStore = newEmptyKeyStore()
    val privateKeySpec = if (privateKeys.isEmpty()) PemComboFile.convertPKCS1toPKCS8(
        privateRsaKeys[0])
    else PKCS8EncodedKeySpec(privateKeys[0].toByteArray())
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKey = keyFactory.generatePrivate(privateKeySpec)
    keyStore.setKeyEntry("key", privateKey, passphrase.toCharArray(),
        decodeCertificates().toTypedArray())
    return CertStore(keyStore)
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

  companion object {
    const val FORMAT_PEM = "PEM"
    const val FORMAT_JCEKS = "JCEKS"
  }
}
