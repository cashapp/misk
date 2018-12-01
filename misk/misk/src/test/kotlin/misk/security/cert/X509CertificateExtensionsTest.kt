package misk.security.cert

import misk.security.ssl.PemComboFile
import okio.buffer
import okio.source
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.security.cert.X509Certificate

internal class X509CertificateExtensionsTest {
  private val caCert = loadPem("/ssl/ca-cert.pem")
  private val signedByCaCert = loadPem("/ssl/signed-by-ca-cert.pem")

  @Test fun isSelfSigned() {
    assertThat(caCert.isSelfSigned).isTrue()
    assertThat(signedByCaCert.isSelfSigned).isFalse()
  }

  @Test fun isSignedBy() {
    assertThat(caCert.isSignedBy(signedByCaCert)).isFalse()
    assertThat(signedByCaCert.isSignedBy(caCert)).isTrue()
  }

  private fun loadPem(name: String): X509Certificate {
    val source = X509CertificateExtensionsTest::class.java.getResourceAsStream(name).source()
    return PemComboFile.parse(source.buffer()).decodeCertificates().map {
      it as X509Certificate
    }.first()
  }
}