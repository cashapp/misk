package misk.crypto

import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.name.Names
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
  fun testImportKey() {
    val keyHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val encryptedKey = generateEncryptedKey(keyHandle)
    val key = Key("test", listOf(EncryptedKey(encryptedKey, "test")))
    val config = CryptoConfig(listOf(key))
    val injector = Guice.createInjector(CryptoTestModule(), CryptoModule(config))
    val keyManager = injector.getInstance(KeyManager::class.java)
    val testKey = keyManager["test"]
    assertThat(testKey).isNotNull()
    val cipher = injector.getInstance(com.google.inject.Key.get(Cipher::class.java, Names.named("test")))
    assertThat(cipher).isNotNull()
  }

  @Test
  fun testInvalidConfig() {
    val keyHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val encryptedKey = generateEncryptedKey(keyHandle)
    val config = CryptoConfig(listOf(
        Key("key_name", listOf(
            EncryptedKey(encryptedKey, "AWS master key alias", "GCP master key URI")
        ))
    ))
    assertThatThrownBy { Guice.createInjector(CryptoTestModule(), CryptoModule(config)) }
        .isInstanceOf(CreationException::class.java)
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