package misk.security.ssl

import java.security.KeyFactory
import java.security.KeyStore
import java.security.spec.PKCS8EncodedKeySpec

/**
 * This class represents a certificate and the private key for that certificate.
 */
data class CertStore(val keyStore: KeyStore) {
  companion object {
    /**
     * Load a CertStore from a combined PEM file, returning null in the event that it doesn't
     * contain a single private key and a cert chain.
     */
    fun load(certKeyComboPath: String, passPhrase: String? = null): CertStore?
        = PemComboFile.load(certKeyComboPath, passPhrase).toCertStore()

    // TODO(young): This function should check that the underlying keystore complies with what
    // CertStore provides.
    fun load(keyStore: KeyStore) = CertStore(keyStore)

    fun PemComboFile.toCertStore(): CertStore? {
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
  }
}
