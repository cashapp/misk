package misk.security.ssl

import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
internal class PemComboFileTest {
  @MiskTestModule
  val module = MiskTestingServiceModule()

  val clientComboPemPath = "classpath:/ssl/client_cert_key_combo.pem"
  val clientRsaComboPemPath = "classpath:/ssl/client_rsa_cert_key_combo.pem"
  val clientCertPemPath = "classpath:/ssl/client_cert.pem"

  @Inject lateinit var sslLoader: SslLoader

  @Test
  fun loadTruststoreFromPEM() {
    val keystore = sslLoader.loadTrustStore(clientCertPemPath)!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("0")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadRsaCertStoreFromPEM() {
    val keystore = sslLoader.loadCertStore(clientRsaComboPemPath)!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("key")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadCertStoreFromPEM() {
    val keystore = sslLoader.loadCertStore(clientComboPemPath)!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("key")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")

    val certificateAndKey = keystore.getCertificateAndKey("password".toCharArray())
    assertThat(certificateAndKey).isNotNull()
    assertThat(certificateAndKey!!.certificate.issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(certificateAndKey.certificate.subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")

    val certificateChain = keystore.getX509CertificateChain()
    assertThat(certificateChain.map { it.issuerX500Principal.name }).containsExactly(
        "CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
  }
}
