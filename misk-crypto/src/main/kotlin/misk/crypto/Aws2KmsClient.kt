package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KmsClient
import java.security.GeneralSecurityException
import java.util.Locale
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.EncryptRequest

/**
 * A Tink [KmsClient] backed by the AWS SDK v2 KMS client.
 *
 * This is a drop-in replacement for `com.google.crypto.tink.integration.awskms.AwsKmsClient` (which is built on the
 * deprecated AWS SDK v1). It is wire-compatible with it:
 * - key URIs use the same `aws-kms://<key-arn>` format
 * - non-empty associated data is passed as the KMS encryption context entry `associatedData=<lowercase hex of the AAD
 *   bytes>`, exactly like Tink's v1 client, so ciphertexts produced by either implementation decrypt with the other.
 */
class Aws2KmsClient(private val kms: software.amazon.awssdk.services.kms.KmsClient) : KmsClient {

  override fun doesSupport(keyUri: String?): Boolean = keyUri?.lowercase(Locale.US)?.startsWith(PREFIX) ?: false

  @Deprecated("Credentials are configured on the AWS SDK v2 KmsClient passed to the constructor.")
  override fun withCredentials(credentialPath: String?): KmsClient = this

  @Deprecated("Credentials are configured on the AWS SDK v2 KmsClient passed to the constructor.")
  override fun withDefaultCredentials(): KmsClient = this

  override fun getAead(keyUri: String?): Aead {
    require(keyUri != null && doesSupport(keyUri)) { "key URI must start with $PREFIX" }
    return Aws2KmsAead(kms, keyUri.substring(PREFIX.length))
  }

  companion object {
    const val PREFIX = "aws-kms://"
  }
}

/**
 * An [Aead] that encrypts/decrypts with a single AWS KMS key using the AWS SDK v2.
 *
 * Ciphertext-compatible with Tink's v1 `AwsKmsAead`: KMS Encrypt/Decrypt are server-side operations, and the
 * associated-data-to-encryption-context mapping matches Tink's.
 */
internal class Aws2KmsAead(private val kms: software.amazon.awssdk.services.kms.KmsClient, private val keyArn: String) :
  Aead {

  override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
    try {
      val request =
        EncryptRequest.builder()
          .keyId(keyArn)
          .plaintext(SdkBytes.fromByteArray(plaintext))
          .apply {
            if (associatedData != null && associatedData.isNotEmpty()) {
              encryptionContext(mapOf(AAD_CONTEXT_KEY to associatedData.toHex()))
            }
          }
          .build()
      return kms.encrypt(request).ciphertextBlob().asByteArray()
    } catch (e: SdkException) {
      throw GeneralSecurityException("encryption failed", e)
    }
  }

  override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
    try {
      val request =
        DecryptRequest.builder()
          .keyId(keyArn)
          .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
          .apply {
            if (associatedData != null && associatedData.isNotEmpty()) {
              encryptionContext(mapOf(AAD_CONTEXT_KEY to associatedData.toHex()))
            }
          }
          .build()
      val result = kms.decrypt(request)
      // If the key ARN is a full key ARN (not an alias), make sure KMS used that exact key.
      if (isKeyArnFormat(keyArn) && result.keyId() != keyArn) {
        throw GeneralSecurityException("decryption failed: wrong key id")
      }
      return result.plaintext().asByteArray()
    } catch (e: SdkException) {
      throw GeneralSecurityException("decryption failed", e)
    }
  }

  companion object {
    /** Matches Tink v1's `AwsKmsAead` encryption context key for associated data. */
    private const val AAD_CONTEXT_KEY = "associatedData"

    /** Matches Tink v1's `AwsKmsAead.isKeyArnFormat`. */
    internal fun isKeyArnFormat(keyArn: String): Boolean {
      val parts = keyArn.split(":")
      return parts.size == 6 && parts[5].startsWith("key/")
    }

    /** Lowercase hex, matching AWS SDK v1's `BinaryUtils.toHex` used by Tink v1. */
    internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
  }
}
