package misk.cloud.fake.fake.security.keys

import misk.cloud.fake.security.keys.FakeKeyService
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FakeKeyServiceTest {
  @Test
  fun encryptDecryptSmallKeys() {
    val keyManager = FakeKeyService()
    val keyAlias = "my-small-key"
    val plainText = "encrypt me!".encodeUtf8()
    val cipherText = keyManager.encrypt(keyAlias, plainText)
    assertThat(cipherText).isNotEqualTo(plainText)

    val recoveredPlainText = keyManager.decrypt(keyAlias, cipherText)
    assertThat(recoveredPlainText).isEqualTo(plainText)
  }

  @Test
  fun encryptDecyptLargeKeys() {
    val keyManager = FakeKeyService()
    val keyAlias = "my-very-long-key-which-exceeds-256-bytes-and-therefore-is-truncated"
    val plainText = "encrypt me!".encodeUtf8()
    val cipherText = keyManager.encrypt(keyAlias, plainText)
    assertThat(cipherText).isNotEqualTo(plainText)

    val recoveredPlainText = keyManager.decrypt(keyAlias, cipherText)
    assertThat(recoveredPlainText).isEqualTo(plainText)
  }
}
