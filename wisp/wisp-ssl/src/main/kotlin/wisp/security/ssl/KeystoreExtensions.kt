package wisp.security.ssl

import java.security.*
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(
      expression = "CertificateAndKey(certificate, privateKey)",
      imports = ["misk.security.ssl.CertificateAndKey"],
    ),
)
data class CertificateAndKey(val certificate: X509Certificate, val privateKey: PrivateKey)

/** @return the certificate and key pair for the given alias */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(
      expression = "getCertificateAndKey(alias, passphrase)",
      imports = ["misk.security.ssl.getCertificateAndKey"],
    ),
)
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
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(expression = "getCertificateAndKey(passphrase)", imports = ["misk.security.ssl.getCertificateAndKey"]),
)
fun KeyStore.getCertificateAndKey(passphrase: CharArray) = getCertificateAndKey(onlyAlias, passphrase)

/** @return the only alias in the keystore, if the keystore only has a single entry */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "onlyAlias", imports = ["misk.security.ssl.onlyAlias"]),
)
val KeyStore.onlyAlias: String
  get() {
    val aliases = aliases()
    val element = aliases.nextElement()
    check(element != null) { "no alias found in keystore" }
    check(!aliases.hasMoreElements()) { "more than one alias in keystore " }
    return element
  }

/** @return the [PrivateKey] with the given alias */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(expression = "getPrivateKey(alias, passphrase)", imports = ["misk.security.ssl.getPrivateKey"]),
)
fun KeyStore.getPrivateKey(alias: String, passphrase: CharArray): PrivateKey {
  try {
    return getKey(alias, passphrase) as? PrivateKey ?: throw IllegalStateException("no private key with alias $alias")
  } catch (e: NoSuchAlgorithmException) {
    throw IllegalStateException("Algorithm for reading key not available", e)
  } catch (e: UnrecoverableKeyException) {
    throw IllegalArgumentException("Invalid password for reading key $alias", e)
  }
}

/** @return the one and only [PrivateKey] in the keystore */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "getPrivateKey(passphrase)", imports = ["misk.security.ssl.getPrivateKey"]),
)
fun KeyStore.getPrivateKey(passphrase: CharArray) = getPrivateKey(onlyAlias, passphrase)

/** @return all aliases present in the keystore of a given entry type. */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "aliasesOfType(entryClass)", imports = ["misk.security.ssl.aliasesOfType"]),
)
fun KeyStore.aliasesOfType(entryClass: Class<out KeyStore.Entry>): List<String> {
  return aliases().asSequence().filter { entryInstanceOf(it, entryClass) }.toList()
}

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "aliasesOfType()", imports = ["misk.security.ssl.aliasesOfType"]),
)
inline fun <reified T : KeyStore.Entry> KeyStore.aliasesOfType() = aliasesOfType(T::class.java)

/** @return the [X509Certificate] chain with the provided alias */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(expression = "getX509CertificateChain(alias)", imports = ["misk.security.ssl.getX509CertificateChain"]),
)
fun KeyStore.getX509CertificateChain(alias: String): Array<X509Certificate> {
  require(alias.isNotBlank()) { "alias must not be empty or blank" }

  val certs = getCertificateChain(alias)
  check(certs != null && certs.isNotEmpty()) { "no certificate chain found for alias $alias" }

  return certs.mapNotNull { it as? X509Certificate }.toTypedArray()
}

/** @return the one and only [X509Certificate] chain in the keystore */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(expression = "getX509CertificateChain()", imports = ["misk.security.ssl.getX509CertificateChain"]),
)
fun KeyStore.getX509CertificateChain() = getX509CertificateChain(onlyAlias)

/** @return The [X509Certificate] with the provided alias */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith =
    ReplaceWith(expression = "getX509Certificate(alias)", imports = ["misk.security.ssl.getX509Certificate"]),
)
fun KeyStore.getX509Certificate(alias: String): X509Certificate {
  require(alias.isNotBlank()) { "alias must not be empty or blank" }

  val cert = getCertificate(alias) ?: throw IllegalStateException("no certificate found for alias $alias")

  return cert as? X509Certificate ?: throw IllegalStateException("certificate for $alias is not an X509 certificate")
}

/** @return the one and only [X509Certificate] in the keystore */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "getX509Certificate()", imports = ["misk.security.ssl.getX509Certificate"]),
)
fun KeyStore.getX509Certificate() = getX509Certificate(onlyAlias)

private val trustAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
