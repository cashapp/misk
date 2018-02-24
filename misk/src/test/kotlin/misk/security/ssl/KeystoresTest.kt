package misk.security.ssl

import misk.resources.ResourceLoader
import misk.security.ssl.Keystores.TYPE_JCEKS
import misk.security.ssl.Keystores.TYPE_PEM
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyStore

internal class KeystoresTest {
    @Test
    fun loadFromPEM() {
        val input = ResourceLoader.open("ssl/client_cert.pem")!!.inputStream()
        val keystore = Keystores.load(input, TYPE_PEM, null)
        assertThat(keystore.aliases().toList()).containsExactly("0")
        assertThat((keystore.getX509Certificate()).issuerX500Principal.name)
                .isEqualTo("CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US")
        assertThat(keystore.getX509Certificate().subjectAlternativeNames.toList()[0][1])
                .isEqualTo("127.0.0.1")
    }

    @Test
    fun loadFromJCEKS() {
        val input = ResourceLoader.open("ssl/server_keystore.jceks")!!.inputStream()
        val keystore = Keystores.load(input, TYPE_JCEKS, "serverpassword")
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
}