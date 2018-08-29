package misk.security.ssl

import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import java.io.IOException
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.KeySpec
import java.security.spec.RSAPrivateCrtKeySpec

/**
 * A file containing a mix of PEM-encoded certificates and PEM-encoded private
 * keys. Can be used both for trust stores (which certificate authorities a TLS
 * client trusts) and also for TLS servers (which certificate chain a TLS server
 * serves).
 */
data class PemComboFile(
  val certificates: List<ByteString>,
  val privateRsaKeys: List<ByteString>,
  val privateKeys: List<ByteString>,
  val passphrase: String
) {
  fun newEmptyKeyStore(): KeyStore {
    val password = passphrase.toCharArray() // Any password will work.

    val result = KeyStore.getInstance(KeyStore.getDefaultType())
    result.load(null, password) // By convention, null input creates a new empty store
    return result
  }

  fun decodeCertificates(): List<Certificate> {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    return certificates.map {
      certificateFactory.generateCertificate(Buffer().write(it).inputStream())
    }
  }

  companion object {
    fun parse(certKeyComboSource: BufferedSource, passphrase: String? = null): PemComboFile {
      val certificates = mutableListOf<ByteString>()
      val privateRsaKeys = mutableListOf<ByteString>()
      val privateKeys = mutableListOf<ByteString>()

      val lines = certKeyComboSource.lines().iterator()
      while (lines.hasNext()) {
        val line = lines.next()

        when {
          line.matches(Regex("-+BEGIN CERTIFICATE-+")) -> {
            certificates += decodeBase64Until(lines,
                Regex("-+END CERTIFICATE-+"))
          }
          line.matches(Regex("-+BEGIN RSA PRIVATE KEY-+")) -> {
            privateRsaKeys += decodeBase64Until(lines,
                Regex("-+END RSA PRIVATE KEY-+"))
          }
          line.matches(Regex("-+BEGIN PRIVATE KEY-+")) -> {
            privateKeys += decodeBase64Until(lines,
                Regex("-+END PRIVATE KEY-+"))
          }
          line.isBlank() -> {
            // This is ok, just keep going
          }
          else -> throw IOException("unexpected line: $line")
        }
      }

      return PemComboFile(certificates, privateRsaKeys, privateKeys,
          passphrase ?: "password")
    }

    fun convertPKCS1toPKCS8(pkcs1Key: ByteString): KeySpec {
      val keyObject = ASN1Sequence.fromByteArray(pkcs1Key.toByteArray())
      val rsaPrivateKey = RSAPrivateKey.getInstance(keyObject)

      return RSAPrivateCrtKeySpec(
          rsaPrivateKey.modulus,
          rsaPrivateKey.publicExponent,
          rsaPrivateKey.privateExponent,
          rsaPrivateKey.prime1,
          rsaPrivateKey.prime2,
          rsaPrivateKey.exponent1,
          rsaPrivateKey.exponent2,
          rsaPrivateKey.coefficient
      )
    }

    private fun BufferedSource.lines(): List<String> {
      use {
        val result = mutableListOf<String>()
        while (true) {
          val line = readUtf8Line() ?: break
          result.add(line)
        }
        return result
      }
    }

    private fun decodeBase64Until(lines: Iterator<String>, until: Regex): ByteString {
      val result = Buffer()

      while (true) {
        if (!lines.hasNext()) throw IOException("$until not found")

        val line = lines.next()
        if (line.matches(until)) break
        if (line.isEmpty()) continue
        result.writeUtf8(line)
      }

      return result.readUtf8().decodeBase64() ?: throw IOException("malformed base64")
    }
  }
}