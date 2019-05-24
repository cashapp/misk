package misk.crypto

import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.mac.MacKeyTemplates
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.Injector
import misk.config.MiskConfig
import misk.config.Secret
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException

@MiskTest
class CryptoModuleTest {

  init {
    AeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()
  }

  @Test
  fun testImportAeadKey() {
    val keyHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val testKey = injector.getInstance(AeadKeyManager::class.java)["test"]
    assertThat(testKey).isNotNull()
  }

  @Test
  fun testAeadIsEnvelopeKey() {
    val keyHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val testKey = injector.getInstance(AeadKeyManager::class.java)["test"]
    assertThat(testKey).isInstanceOf(KmsEnvelopeAead::class.java)
  }

  @Test
  fun testImportMacKey() {
    val keyHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val injector = getInjector(listOf(Pair("test-mac", keyHandle)))
    val mac = injector.getInstance(MacKeyManager::class.java)["test-mac"]
    assertThat(mac).isNotNull()
  }

  @Test
  fun testImportDigitalSignature() {
    val keyHandle = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
    val injector = getInjector(listOf(Pair("test-ds", keyHandle)))
    val keyManager = injector.getInstance(DigitalSignatureKeyManager::class.java)
    val signer = keyManager.getSigner("test-ds")
    val verifier = keyManager.getVerifier("test-ds")

    val data = "sign this".toByteArray()
    val signature = signer.sign(data)
    assertThatCode { verifier.verify(signature, data) }
        .doesNotThrowAnyException()
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
    assertThatThrownBy { injector.getInstance(MacKeyManager::class.java)["not there"] }
            .isInstanceOf(KeyNotFoundException::class.java)
    assertThatThrownBy { injector.getInstance(AeadKeyManager::class.java)["not there either"]}
            .isInstanceOf(KeyNotFoundException::class.java)
  }

  @Test
  fun testRaisesInWrongEnv() {

    val plainKey = Key("name", KeyType.AEAD, MiskConfig.RealSecret(""))
    val kr = KeyReader()
    val client = FakeKmsClient()

    assertThatThrownBy {
      kr.env = Environment.STAGING
      kr.readKey(plainKey, null, client)
    }.isInstanceOf(GeneralSecurityException::class.java)

    assertThatThrownBy {
      kr.env = Environment.PRODUCTION
      kr.readKey(plainKey, null, client)
    }.isInstanceOf(GeneralSecurityException::class.java)
  }

  private fun getInjector(keyMap: List<Pair<String, KeysetHandle>>): Injector{
    val keys = keyMap.map {
      var keyType = KeyType.AEAD
      val keyTypeUrl = it.second.keysetInfo.getKeyInfo(0).typeUrl
      if (keyTypeUrl.endsWith("HmacKey")) {
        keyType = KeyType.MAC
      } else if (keyTypeUrl.endsWith("Ed25519PrivateKey")) {
        keyType = KeyType.DIGITAL_SIGNATURE
      }
      Key(it.first, keyType, generateEncryptedKey(it.second))
    }
    val config = CryptoConfig(keys, "test_master_key")
    val envModule = EnvironmentModule(Environment.TESTING)
    return Guice.createInjector(CryptoTestModule(), CryptoModule(config), envModule)
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