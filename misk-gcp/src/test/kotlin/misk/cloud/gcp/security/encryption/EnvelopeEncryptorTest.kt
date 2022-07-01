package misk.cloud.gcp.security.encryption

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest
class EnvelopeEncryptorTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    DeploymentModule(TESTING),
    EnvelopeEncryptionModule(null, TESTING)
  )

  @Inject lateinit var envelopeEncryptor: EnvelopeEncryptor

  @Test
  fun `encryption returns envelope encrypted data`() {
    val plaintextToEncrypt = "to_encrypt"
    val envelopeEncryptedData = envelopeEncryptor.encrypt(plaintextToEncrypt.toByteArray())

    assertThat(envelopeEncryptedData).isNotEqualTo(plaintextToEncrypt)
  }
}
