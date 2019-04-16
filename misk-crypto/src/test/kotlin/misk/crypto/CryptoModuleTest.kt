package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Mac
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.mac.MacKeyTemplates
import com.google.inject.CreationException
import com.google.inject.Guice
import misk.config.Secret
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.ByteArrayOutputStream

@MiskTest
class CryptoModuleTest {

  init {
    AeadConfig.register()
  }

  @Test
  fun testImportAeadKey() {
    val keyHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val keyManager = getKeyManager(listOf(Pair("test", keyHandle)))
    val testKey = keyManager.get<Aead>("test")
    assertThat(testKey).isNotNull()
  }

  @Test
  fun testImportMacKey() {
    val keyHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val keyManager = getKeyManager(listOf(Pair("test-mac", keyHandle)))
    val mac = keyManager.get<Mac>("test-mac")
    assertThat(mac).isNotNull()
  }

  @Test
  fun testMultipleKeys() {
    val aeadHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val macHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val keyManager = getKeyManager(listOf(Pair("aead", aeadHandle), Pair("mac", macHandle)))
    assertThat(keyManager.get<Aead>("aead")).isNotNull()
    assertThat(keyManager.get<Mac>("mac")).isNotNull()
  }

  @Test
  fun testDuplicateNames() {
    val key1 = KeysetHandle.generateNew(AeadKeyTemplates.AES256_CTR_HMAC_SHA256)
    val key2 = KeysetHandle.generateNew(AeadKeyTemplates.AES256_CTR_HMAC_SHA256)
    assertThatThrownBy { getKeyManager(listOf(Pair("aead", key1), Pair("aead", key2))) }
        .isInstanceOf(CreationException::class.java)
  }

  @Test
  fun testNoKeyLoaded() {
    val keyManager = getKeyManager(listOf())
    assertThat(keyManager.get<Mac>("not there")).isNull()
    assertThat(keyManager.get<Aead>("not there either")).isNull()
  }

  private fun getKeyManager(keyMap: List<Pair<String, KeysetHandle>>): KeyManager {
    val keys = keyMap.map {
      val keyType: KeyType
      if (it.second.keysetInfo.getKeyInfo(0).typeUrl.endsWith("HmacKey")) {
        keyType = KeyType.MAC
      } else {
        keyType = KeyType.ENCRYPTION
      }
      Key(it.first, keyType, generateEncryptedKey(it.second))
    }
    val config = CryptoConfig(keys, "test_master_key")
    val injector = Guice.createInjector(CryptoTestModule(), CryptoModule(config))
    return injector.getInstance(KeyManager::class.java)
  }

  private fun generateEncryptedKey(keyHandle: KeysetHandle): Secret<String> {
    val masterKey = FakeMasterEncryptionKey()
    val keyOutputStream = ByteArrayOutputStream()
    keyHandle.write(JsonKeysetWriter.withOutputStream(keyOutputStream), masterKey)
    return object : Secret<String> {
      override val value: String
        get() = keyOutputStream.toString()
    }
  }
}