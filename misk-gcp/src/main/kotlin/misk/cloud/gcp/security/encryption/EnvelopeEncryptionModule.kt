package misk.cloud.gcp.security.encryption

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.crypto.tink.KmsClients
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient
import java.lang.IllegalArgumentException
import misk.inject.KAbstractModule
import wisp.deployment.Deployment
import wisp.deployment.getDeploymentFromEnvironmentVariable

class EnvelopeEncryptionModule(
  private val config: EnvelopeEncryptionConfig?,
  private val deployment: Deployment = getDeploymentFromEnvironmentVariable(),
) : KAbstractModule() {
  override fun configure() {
    AeadConfig.register()

    if (deployment.isReal) {
      bind<EnvelopeEncryptionConfig>().toInstance(config)
      registerCloudKmsClient(config!!)
      bind<EnvelopeEncryptor>().to<RealEnvelopeEncryptor>()
    } else {
      bind<EnvelopeEncryptor>().to<FakeEnvelopeEncryptor>()
    }
  }

  private fun registerCloudKmsClient(config: EnvelopeEncryptionConfig) {
    val cloudKmsClient = GcpKmsClient(config.kekUri)

    try {
      cloudKmsClient.withCredentials(
        GoogleCredential.fromStream(config.credentials.value.byteInputStream())
      )
    } catch (e: IllegalArgumentException) {
    }
    KmsClients.add(cloudKmsClient)
  }
}

