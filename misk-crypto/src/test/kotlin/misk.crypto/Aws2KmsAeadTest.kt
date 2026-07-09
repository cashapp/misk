package misk.crypto

import java.security.GeneralSecurityException
import misk.crypto.Aws2KmsAead.Companion.isKeyArnFormat
import misk.crypto.Aws2KmsAead.Companion.toHex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.DecryptResponse
import software.amazon.awssdk.services.kms.model.EncryptRequest
import software.amazon.awssdk.services.kms.model.EncryptResponse

class Aws2KmsAeadTest {

  private val keyArn = "arn:aws:kms:us-west-2:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab"

  @Test
  fun `associated data is passed as hex encryption context, matching tink-awskms`() {
    // Tink v1's AwsKmsAead uses BinaryUtils.toHex (lowercase hex) under the "associatedData" key.
    val recorded = RecordingKms()
    val aead = Aws2KmsAead(recorded, keyArn)

    aead.encrypt("hello".toByteArray(), byteArrayOf(0x00, 0x0F, 0xAB.toByte(), 0xFF.toByte()))

    assertThat(recorded.lastEncrypt!!.encryptionContext())
      .containsExactlyEntriesOf(mapOf("associatedData" to "000fabff"))

    aead.decrypt(byteArrayOf(1, 2, 3), byteArrayOf(0x00, 0x0F, 0xAB.toByte(), 0xFF.toByte()))

    assertThat(recorded.lastDecrypt!!.encryptionContext())
      .containsExactlyEntriesOf(mapOf("associatedData" to "000fabff"))
  }

  @Test
  fun `empty or null associated data omits the encryption context`() {
    val recorded = RecordingKms()
    val aead = Aws2KmsAead(recorded, keyArn)

    aead.encrypt("hello".toByteArray(), null)
    assertThat(recorded.lastEncrypt!!.encryptionContext()).isEmpty()

    aead.encrypt("hello".toByteArray(), byteArrayOf())
    assertThat(recorded.lastEncrypt!!.encryptionContext()).isEmpty()
  }

  @Test
  fun `decrypt verifies the responding key id for full key ARNs`() {
    val recorded = RecordingKms(respondingKeyId = "arn:aws:kms:us-west-2:111122223333:key/other-key")
    val aead = Aws2KmsAead(recorded, keyArn)

    assertThatThrownBy { aead.decrypt(byteArrayOf(1, 2, 3), null) }
      .isInstanceOf(GeneralSecurityException::class.java)
      .hasMessageContaining("wrong key id")
  }

  @Test
  fun `decrypt does not verify key id for aliases`() {
    val alias = "alias/my-key"
    val recorded = RecordingKms(respondingKeyId = keyArn)
    val aead = Aws2KmsAead(recorded, alias)

    assertThat(aead.decrypt(byteArrayOf(1, 2, 3), null)).isEqualTo("plaintext".toByteArray())
  }

  @Test
  fun `isKeyArnFormat matches tink-awskms`() {
    assertThat(isKeyArnFormat(keyArn)).isTrue()
    assertThat(isKeyArnFormat("alias/my-key")).isFalse()
    assertThat(isKeyArnFormat("arn:aws:kms:us-west-2:111122223333:alias/my-key")).isFalse()
  }

  @Test
  fun `toHex is lowercase and zero padded`() {
    assertThat(byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte()).toHex()).isEqualTo("00017f80ff")
  }

  @Test
  fun `client only supports aws-kms uris`() {
    val client = Aws2KmsClient(RecordingKms())
    assertThat(client.doesSupport("aws-kms://$keyArn")).isTrue()
    assertThat(client.doesSupport("AWS-KMS://$keyArn")).isTrue()
    assertThat(client.doesSupport("gcp-kms://foo")).isFalse()
    assertThat(client.doesSupport(null)).isFalse()

    assertThatThrownBy { client.getAead("gcp-kms://foo") }.isInstanceOf(IllegalArgumentException::class.java)
  }

  /** A [KmsClient] that records requests and returns canned responses. */
  private class RecordingKms(private val respondingKeyId: String? = null) : KmsClient {
    var lastEncrypt: EncryptRequest? = null
    var lastDecrypt: DecryptRequest? = null

    override fun encrypt(request: EncryptRequest): EncryptResponse {
      lastEncrypt = request
      return EncryptResponse.builder()
        .keyId(respondingKeyId ?: request.keyId())
        .ciphertextBlob(SdkBytes.fromByteArray("ciphertext".toByteArray()))
        .build()
    }

    override fun decrypt(request: DecryptRequest): DecryptResponse {
      lastDecrypt = request
      return DecryptResponse.builder()
        .keyId(respondingKeyId ?: request.keyId())
        .plaintext(SdkBytes.fromByteArray("plaintext".toByteArray()))
        .build()
    }

    override fun serviceName(): String = "kms"

    override fun close() {}
  }
}
