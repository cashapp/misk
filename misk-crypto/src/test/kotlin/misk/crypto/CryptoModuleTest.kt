package misk.crypto

import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.mac.MacKeyTemplates
import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.Injector
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
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val testKey = injector.getInstance(AeadKeyManager::class.java)["test"]
    assertThat(testKey).isNotNull()
  }

  @Test
  fun testImportMacKey() {
    val keyHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val injector = getInjector(listOf(Pair("test-mac", keyHandle)))
    val mac = injector.getInstance(MacKeyManager::class.java)["test-mac"]
    assertThat(mac).isNotNull()
  }

  @Test
  fun testMultipleKeys() {
    val aeadHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val macHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val injector = getInjector(listOf(Pair("aead", aeadHandle), Pair("mac", macHandle)))
    assertThat(injector.getInstance(AeadKeyManager::class.java)["aead"]).isNotNull()
    assertThat(injector.getInstance(MacKeyManager::class.java)["mac"]).isNotNull()
  }

  @Test
  fun testDuplicateNames() {
    val key1 = KeysetHandle.generateNew(AeadKeyTemplates.AES256_CTR_HMAC_SHA256)
    val key2 = KeysetHandle.generateNew(AeadKeyTemplates.AES256_CTR_HMAC_SHA256)
    assertThatThrownBy { getInjector(listOf(Pair("aead", key1), Pair("aead", key2))) }
        .isInstanceOf(CreationException::class.java)
  }

  @Test
  fun testNoKeyLoaded() {
    val injector = getInjector(listOf())
    assertThat(injector.getInstance(MacKeyManager::class.java)["not there"]).isNull()
    assertThat(injector.getInstance(AeadKeyManager::class.java)["not there either"]).isNull()
  }

  private fun getInjector(keyMap: List<Pair<String, KeysetHandle>>): Injector{
    val keys = keyMap.map {
      var keyType = KeyType.ENCRYPTION
      if (it.second.keysetInfo.getKeyInfo(0).typeUrl.endsWith("HmacKey")) {
        keyType = KeyType.MAC
      }
      Key(it.first, keyType, generateEncryptedKey(it.second))
    }
    val config = CryptoConfig(keys, "test_master_key")
    return  Guice.createInjector(CryptoTestModule(), CryptoModule(config))
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