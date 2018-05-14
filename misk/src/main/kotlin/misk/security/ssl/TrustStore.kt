package misk.security.ssl

import java.security.KeyStore

data class TrustStore(val keyStore: KeyStore) {
  companion object {
    /**
     * Load a TrustStore from a combined PEM file, returning null in the event that it contains
     * a certificate chain and no private keys.
     */
    fun load(certKeyComboPath: String, passPhrase: String? = null): TrustStore?
        = PemComboFile.load(certKeyComboPath, passPhrase).toTrustStore()

    // TODO(young): This function should check that the underlying keystore complies with what
    // TrustStore provides.
    fun load(keyStore: KeyStore) = TrustStore(keyStore)

    fun PemComboFile.toTrustStore(): TrustStore? {
      if (!privateKeys.isEmpty() || !privateRsaKeys.isEmpty()) return null

      val keyStore = newEmptyKeyStore()
      decodeCertificates().forEachIndexed { index, certificate ->
        keyStore.setCertificateEntry(index.toString(), certificate)
      }
      return TrustStore(keyStore)
    }
  }
}