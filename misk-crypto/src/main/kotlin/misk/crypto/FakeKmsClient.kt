package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KmsClient

/**
 * This fake implementation of a Key Management Service client is meant to be used
 * for testing and development purposes only.
 *
 * When calling `getAead`, it'll provide a fake [Aead] object that **performs no encryption**.
 * Instead, it only does Base64 encoding/decoding so developers could debug their apps.
 */
@Deprecated("Use misk-crypto-testing instead",
  replaceWith = ReplaceWith("FakeKmsClient", imports = ["misk.crypto.testing"]))
internal class FakeKmsClient : KmsClient {
  override fun getAead(keyUri: String?): Aead {
    return FakeMasterEncryptionKey()
  }

  override fun doesSupport(keyUri: String?): Boolean = true
  override fun withCredentials(credentialPath: String?): KmsClient = this
  override fun withDefaultCredentials(): KmsClient = this
}
