package wisp.security.ssl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.resources.ResourceLoader
import java.security.KeyStore

internal class SslLoaderTest {

  val clientComboPemPath = "classpath:/ssl/client_cert_key_combo.pem"
  val clientTrustPemPath = "classpath:/ssl/client_cert.pem"
  val serverKeystoreJceksPath = "classpath:/ssl/server_keystore.jceks"
  val keystoreJksPath = "classpath:/ssl/keystore.jks"
  val truststoreJksPath = "classpath:/ssl/truststore.jks"
  val keystoreP12Path = "classpath:/ssl/keystore.p12"
  val truststoreP12Path = "classpath:/ssl/truststore.p12"

  val sslLoader: SslLoader = SslLoader(ResourceLoader.SYSTEM)

  @Test
  fun loadKeystoreFromPEM() {
    val keystore = sslLoader.loadCertStore(
      clientComboPemPath,
      SslLoader.Companion.FORMAT_PEM, "password"
    )!!.keyStore
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
      sslLoader.loadTrustStore(
        clientTrustPemPath,
        SslLoader.Companion.FORMAT_PEM, "serverpassword"
      )!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("0")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
      .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
      .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadFromJCEKS() {
    val keystore = sslLoader.loadTrustStore(
      serverKeystoreJceksPath, SslLoader.Companion.FORMAT_JCEKS, "serverpassword"
    )!!.keyStore
    assertThat(keystore.aliasesOfType<KeyStore.PrivateKeyEntry>()).containsExactly("1")
    assertThat(keystore.getPrivateKey("serverpassword".toCharArray()))
      .isNotNull()

    val certificateAndKey = keystore.getCertificateAndKey("serverpassword".toCharArray())
    assertThat(certificateAndKey).isNotNull()
    assertThat(certificateAndKey!!.certificate.issuerX500Principal.name)
      .isEqualTo("CN=misk-server,OU=Server,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(certificateAndKey.certificate.subjectAlternativeNames.toList()[0][1])
      .isEqualTo("127.0.0.1")

    val certificateChain = keystore.getX509CertificateChain()
    assertThat(certificateChain.map { it.issuerX500Principal.name })
      .containsExactly(
        "CN=misk-server,OU=Server,O=Misk,L=San Francisco,ST=CA,C=US"
      )
  }

  @Test
  fun loadKeystoreFromJKS() {
    val keystore = sslLoader.loadCertStore(
      keystoreJksPath,
      SslLoader.Companion.FORMAT_JKS, "changeit"
    )!!.keyStore
    assertThat(keystore.aliasesOfType<KeyStore.PrivateKeyEntry>()).containsExactly(
      "combined-key-cert"
    )
    assertThat(keystore.getPrivateKey("changeit".toCharArray())).isNotNull()

    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
      .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
      .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadTrustFromJKS() {
    val keystore =
      sslLoader.loadTrustStore(
        truststoreJksPath,
        SslLoader.Companion.FORMAT_JKS, "changeit"
      )!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("ca")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
      .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
      .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadKeystoreFromP12() {
    val keystore = sslLoader.loadCertStore(
      keystoreP12Path,
      SslLoader.Companion.FORMAT_PKCS12, "changeit"
    )!!.keyStore
    assertThat(keystore.aliasesOfType<KeyStore.PrivateKeyEntry>()).containsExactly(
      "combined-key-cert"
    )
    assertThat(keystore.getPrivateKey("changeit".toCharArray())).isNotNull()

    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
      .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
      .isEqualTo("127.0.0.1")
  }

  @Test
  fun loadTrustFromP12() {
    val keystore =
      sslLoader.loadTrustStore(
        truststoreP12Path,
        SslLoader.Companion.FORMAT_PKCS12, "changeit"
      )!!.keyStore
    assertThat(keystore.aliases().toList()).containsExactly("ca")
    assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
      .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
    assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
      .isEqualTo("127.0.0.1")
  }
}
