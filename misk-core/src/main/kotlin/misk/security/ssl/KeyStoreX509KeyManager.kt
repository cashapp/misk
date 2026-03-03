package misk.security.ssl

import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedKeyManager

/**
 * An [X509ExtendedKeyManager] that loads certificates from a [KeyStore]. The [KeyStore]
 * should contain one and only one alias. The [KeyStore] can be lazily supplied, allowing
 * for periodically reloading from disk if needed
 */
internal class KeyStoreX509KeyManager(
  private val passphrase: CharArray,
  private val lazyKeyStore: () -> KeyStore
) : X509ExtendedKeyManager() {

  constructor(passphrase: CharArray, keyStore: KeyStore) : this(passphrase, { keyStore })

  override fun chooseServerAlias(
    keyType: String,
    issuers: Array<out Principal>,
    socket: Socket
  ) = getPrivateKeyAlias()

  override fun chooseClientAlias(
    keyTypes: Array<out String>,
    issuers: Array<out Principal>,
    socket: Socket
  ) = getPrivateKeyAlias()

  override fun getClientAliases(keyType: String, issuers: Array<out Principal>): Array<String> {
    return arrayOf(getPrivateKeyAlias())
  }

  override fun getServerAliases(keyType: String, issuers: Array<out Principal>): Array<String> {
    return arrayOf(getPrivateKeyAlias())
  }

  override fun getCertificateChain(alias: String): Array<X509Certificate> {
    return lazyKeyStore().getX509CertificateChain(alias)
  }

  override fun getPrivateKey(alias: String): PrivateKey {
    return lazyKeyStore().getPrivateKey(alias, passphrase)
  }

  private fun getPrivateKeyAlias(): String {
    return lazyKeyStore().aliasesOfType<KeyStore.PrivateKeyEntry>().single()
  }
}
