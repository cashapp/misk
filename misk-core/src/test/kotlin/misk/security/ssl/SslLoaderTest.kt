package misk.security.ssl

import misk.MiskTestingServiceModule
import misk.security.ssl.SslLoader.Companion.FORMAT_JCEKS
import misk.security.ssl.SslLoader.Companion.FORMAT_JKS
import misk.security.ssl.SslLoader.Companion.FORMAT_PEM
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyStore
import javax.inject.Inject

@MiskTest
internal class SslLoaderTest {
  @MiskTestModule
  val module = MiskTestingServiceModule()

  val clientComboPemPath = "classpath:/ssl/client_cert_key_combo.pem"
  val clientTrustPemPath = "classpath:/ssl/client_cert.pem"
  val serverKeystoreJceksPath = "classpath:/ssl/server_keystore.jceks"
  val keystoreJksPath = "classpath:/ssl/keystore.jks"
  val truststoreJksPath = "classpath:/ssl/truststore.jks"

  @Inject lateinit var sslLoader: SslLoader

  @Test
  fun loadKeystoreFromPEM() {
    val keystore = sslLoader.loadCertStore(clientComboPemPath, FORMAT_PEM, "password")!!.keyStore
    assertThat(keystore.aliasesOfType<KeyStore.PrivateKeyEntry>()).containsExactly("key")
    assertThat(keystore.getPrivateKey("password".toCharArray())).isNotNull()

    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadTrustFromPEM() {
    val keystore =
        sslLoader.loadTrustStore(clientTrustPemPath, FORMAT_PEM, "serverpassword")!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("0")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadFromJCEKS() {
    val keystore = sslLoader.loadTrustStore(
        serverKeystoreJceksPath, FORMAT_JCEKS, "serverpassword")!!.keyStore
    assertThat(keystore.aliasesOfType<KeyStore.PrivateKeyEntry>()).containsExactly("1")
    assertThat(keystore.getPrivateKey("serverpassword".toCharArray())).isNotNull()

    val certificateAndKey = keystore.getCertificateAndKey("serverpassword".toCharArray())
    assertThat(certificateAndKey).isNotNull()
    assertThat(certificateAndKey!!.certificate.issuerX500Principal.name)
        .isEqualTo("CN=misk-server,OU=Server,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(certificateAndKey.certificate.subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")

    val certificateChain = keystore.getX509CertificateChain()
    assertThat(certificateChain.map { it.issuerX500Principal.name }).containsExactly(
        "CN=misk-server,OU=Server,O=Misk,L=San Francisco,ST=CA,C=US")
  }

  @Test
  fun loadKeystoreFromJKS() {
    val keystore = sslLoader.loadCertStore(keystoreJksPath, FORMAT_JKS, "changeit")!!.keyStore
    assertThat(keystore.aliasesOfType<KeyStore.PrivateKeyEntry>()).containsExactly(
        "combined-key-cert")
    assertThat(keystore.getPrivateKey("changeit".toCharArray())).isNotNull()

    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadTrustFromJKS() {
    val keystore =
        sslLoader.loadTrustStore(truststoreJksPath, FORMAT_JKS, "changeit")!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("ca")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
        .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
        .isEqualTo("127.0.0.1")
  }
}
