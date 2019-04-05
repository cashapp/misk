package misk.crypto

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import okio.ByteString.Companion.toByteString
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@MiskTest
class CipherTest {

  init {
    AeadConfig.register()
  }

  private val masterKey = FakeMasterEncryptionKey()
  private val keysetHandle =  KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)

  @Test
  fun testEncryptDecryptRoundTrip() {
    val cipher = RealCipher(listOf(Pair(keysetHandle, masterKey)))
    val plain = "plain".toByteArray().toByteString()
    val encrypted = cipher.encrypt(plain)
    val decrypted = cipher.decrypt(encrypted)
    assertThat(encrypted).isNotEqualTo(plain)
    assertThat(decrypted).isEqualTo(plain)
  }

  @Test
  fun testKeyInfo() {
    val cipher = RealCipher(listOf(Pair(keysetHandle, masterKey)))
    val keysetHandleInfo = keysetHandle.keysetInfo.toString()
    assertThat(cipher.keyInfo.map { it.tinkInfo }).contains(keysetHandleInfo)
  }
}