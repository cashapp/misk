package misk.cloud.gcp.security.keys

import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.cloudkms.v1.CloudKMS
import com.google.api.services.cloudkms.v1.model.DecryptResponse
import com.google.api.services.cloudkms.v1.model.EncryptResponse
import com.google.common.truth.Truth.assertThat
import misk.cloud.gcp.testing.FakeHttpResponse
import misk.cloud.gcp.testing.FakeHttpRouter
import misk.cloud.gcp.testing.FakeHttpRouter.Companion.respondWithError
import misk.cloud.gcp.testing.FakeHttpRouter.Companion.respondWithJson
import okio.ByteString
import org.junit.Test

internal class GcpKeyServiceTest {
  private val config = GcpKmsConfig(
      "my_project",
      mapOf("foo" to GcpKeyLocation("system", "main_ring", "key1"),
          "bar" to GcpKeyLocation("system", "other_ring", "key2"),
          "zed" to GcpKeyLocation("system", "main_ring", "key5")))

  private val TARGET_RESOURCE_URL = "https://cloudkms.googleapis.com/v1/projects/my_project/locations/system/keyRings/main_ring/cryptoKeys/key1"

  private val transport = FakeHttpRouter {
    when (it.url) {
      "$TARGET_RESOURCE_URL:encrypt" -> respondWithJson(EncryptResponse()
          .encodeCiphertext("encrypted".toByteArray(Charsets.UTF_8)))
      "$TARGET_RESOURCE_URL:decrypt" -> respondWithJson(DecryptResponse()
          .encodePlaintext("decrypted".toByteArray(Charsets.UTF_8)))
      else -> respondWithError(404)

    }
  }
  val kms = CloudKMS.Builder(transport, JacksonFactory(), null).build()
  val keyManager = GcpKeyService(kms, config)

  @Test
  fun encrypt() {
    val cipherText = keyManager.encrypt("foo", ByteString.encodeUtf8("encrypt me!"))
    assertThat(cipherText.utf8()).isEqualTo("encrypted")
  }

  @Test
  fun decrypt() {
    val plainText = keyManager.decrypt("foo", ByteString.encodeUtf8("decrypt me!"))
    assertThat(plainText.utf8()).isEqualTo("decrypted")
  }

  @Test(expected = IllegalArgumentException::class)
  fun invalidEncryptKey() {
    keyManager.encrypt("unknown_key", ByteString.encodeUtf8("should fail"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun invalidDecryptKey() {
    keyManager.decrypt("unknown_key", ByteString.encodeUtf8("should fail"))
  }
}
