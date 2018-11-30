package misk.security.ssl

import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory

data class CertificateAndKey(val certificate: X509Certificate, val privateKey: PrivateKey)

/** @return the certificate and key pair for the given alias */
fun KeyStore.getCertificateAndKey(alias: String, passphrase: CharArray): CertificateAndKey? {
  try {
    if (!entryInstanceOf(alias, KeyStore.PrivateKeyEntry::class.java)) return null
    val certificate = getCertificate(alias) as X509Certificate
    val key = getKey(alias, passphrase) as PrivateKey
    return CertificateAndKey(certificate, key)
  } catch (e: KeyStoreException) {
    throw IllegalStateException("Did you forget to call KeyStore.load?", e)
  } catch (e: UnrecoverableKeyException) {
    throw RuntimeException("KeyStore entry password incorrect.", e)
  } catch (e: NoSuchAlgorithmException) {
    throw IllegalStateException(e)
  }
}

/** @return the one and only [CertificateAndKey] in the keystore */
fun KeyStore.getCertificateAndKey(passphrase: CharArray) = getCertificateAndKey(onlyAlias,
    passphrase)

/** @return the only alias in the keystore, if the keystore only has a single entry */
val KeyStore.onlyAlias: String
  get() {
    val aliases = aliases()
    val element = aliases.nextElement()
    check(element != null) { "no alias found in keystore" }
    check(!aliases.hasMoreElements()) { "more than one alias in keystore " }
    return element
  }

/** @return the [PrivateKey] with the given alias */
fun KeyStore.getPrivateKey(alias: String, passphrase: CharArray): PrivateKey {
  try {
    return getKey(alias, passphrase) as? PrivateKey
        ?: throw IllegalStateException("no private key with alias $alias")
  } catch (e: NoSuchAlgorithmException) {
    throw IllegalStateException("Algorithm for reading key not available", e)
  } catch (e: UnrecoverableKeyException) {
    throw IllegalArgumentException("Invalid password for reading key $alias", e)
  }
}

/** @return the one and only [PrivateKey] in the keystore */
fun KeyStore.getPrivateKey(passphrase: CharArray) = getPrivateKey(onlyAlias, passphrase)

/** @return all aliases present in the keystore of a given entry type. */
fun KeyStore.aliasesOfType(entryClass: Class<out KeyStore.Entry>): List<String> {
  return aliases().asSequence().filter { entryInstanceOf(it, entryClass) }.toList()
}

inline fun <reified T : KeyStore.Entry> KeyStore.aliasesOfType() = aliasesOfType(T::class.java)

/** @return the [X509Certificate] chain with the provided alias */
fun KeyStore.getX509CertificateChain(alias: String): Array<X509Certificate> {
  require(alias.isNotBlank()) { "alias must not be empty or blank" }

  val certs = getCertificateChain(alias)
  check(certs != null && certs.isNotEmpty()) {
    "no certificate chain found for alias $alias"
  }

  return certs.mapNotNull { it as? X509Certificate }.toTypedArray()
}

/** @return the one and only [X509Certificate] chain in the keystore */
fun KeyStore.getX509CertificateChain() = getX509CertificateChain(onlyAlias)

/** @return The [X509Certificate] with the provided alias */
fun KeyStore.getX509Certificate(alias: String): X509Certificate {
  require(alias.isNotBlank()) { "alias must not be empty or blank" }

  val cert = getCertificate(alias)
      ?: throw IllegalStateException("no certificate found for alias $alias")

  return cert as? X509Certificate
      ?: throw IllegalStateException("certificate for $alias is not an X509 certificate")
}

/** @return the one and only [X509Certificate] in the keystore */
fun KeyStore.getX509Certificate() = getX509Certificate(onlyAlias)

private val trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
