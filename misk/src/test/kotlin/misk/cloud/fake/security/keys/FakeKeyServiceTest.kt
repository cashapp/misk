package misk.cloud.fake.security.keys

import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FakeKeyServiceTest {
  @Test
  fun encryptDecryptSmallKeys() {
    val keyManager = FakeKeyService()
    val keyAlias = "my-small-key"
    val plainText = ByteString.encodeUtf8("encrypt me!")
    val cipherText = keyManager.encrypt(keyAlias, plainText)
    assertThat(cipherText).isNotEqualTo(plainText)

    val recoveredPlainText = keyManager.decrypt(keyAlias, cipherText)
    assertThat(recoveredPlainText).isEqualTo(plainText)
  }

  @Test
  fun encryptDecyptLargeKeys() {
    val keyManager = FakeKeyService()
    val keyAlias = "my-very-long-key-which-exceeds-256-bytes-and-therefore-is-truncated"
    val plainText = ByteString.encodeUtf8("encrypt me!")
    val cipherText = keyManager.encrypt(keyAlias, plainText)
    assertThat(cipherText).isNotEqualTo(plainText)

    val recoveredPlainText = keyManager.decrypt(keyAlias, cipherText)
    assertThat(recoveredPlainText).isEqualTo(plainText)

  }
}
