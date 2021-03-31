package misk.security.ssl

import wisp.security.ssl.aliasesOfType
import wisp.security.ssl.getCertificateAndKey
import wisp.security.ssl.getPrivateKey
import wisp.security.ssl.getX509Certificate
import wisp.security.ssl.getX509CertificateChain
import wisp.security.ssl.onlyAlias
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import wisp.security.ssl.CertificateAndKey as WispCertificateAndKey

typealias CertificateAndKey = WispCertificateAndKey

/** @return the certificate and key pair for the given alias */
fun KeyStore.getCertificateAndKey(alias: String, passphrase: CharArray): CertificateAndKey? =
  getCertificateAndKey(alias, passphrase)

/** @return the one and only [CertificateAndKey] in the keystore */
fun KeyStore.getCertificateAndKey(passphrase: CharArray) =
  getCertificateAndKey(onlyAlias, passphrase)

/** @return the only alias in the keystore, if the keystore only has a single entry */
val KeyStore.onlyAlias: String
  get() = onlyAlias

/** @return the [PrivateKey] with the given alias */
fun KeyStore.getPrivateKey(alias: String, passphrase: CharArray): PrivateKey =
  getPrivateKey(alias, passphrase)

/** @return the one and only [PrivateKey] in the keystore */
fun KeyStore.getPrivateKey(passphrase: CharArray) = getPrivateKey(onlyAlias, passphrase)

/** @return all aliases present in the keystore of a given entry type. */
fun KeyStore.aliasesOfType(entryClass: Class<out KeyStore.Entry>): List<String> =
  aliasesOfType(entryClass)

inline fun <reified T : KeyStore.Entry> KeyStore.aliasesOfType() = aliasesOfType(T::class.java)

/** @return the [X509Certificate] chain with the provided alias */
fun KeyStore.getX509CertificateChain(alias: String): Array<X509Certificate> =
  getX509CertificateChain(alias)

/** @return the one and only [X509Certificate] chain in the keystore */
fun KeyStore.getX509CertificateChain() = getX509CertificateChain(onlyAlias)

/** @return The [X509Certificate] with the provided alias */
fun KeyStore.getX509Certificate(alias: String): X509Certificate = getX509Certificate(alias)

/** @return the one and only [X509Certificate] in the keystore */
fun KeyStore.getX509Certificate() = getX509Certificate(onlyAlias)
