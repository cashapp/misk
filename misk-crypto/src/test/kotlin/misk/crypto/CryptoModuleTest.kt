package misk.crypto

import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KmsClient
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.daead.DeterministicAeadKeyTemplates
import com.google.crypto.tink.hybrid.HybridKeyTemplates
import com.google.crypto.tink.mac.MacKeyTemplates
import com.google.crypto.tink.signature.SignatureKeyTemplates
import com.google.crypto.tink.streamingaead.StreamingAeadKeyTemplates
import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.config.Secret
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.io.ByteArrayInputStream
import java.util.Base64

@MiskTest(startService = true)
class CryptoModuleTest {
  @Suppress("unused")
  @MiskTestModule
  val module = CryptoTestModule(CryptoConfig(null,"",null))

  @Test
  fun testImportAeadKey() {
    val keyHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val testKey = injector.getInstance(AeadKeyManager::class.java)["test"]
    assertThat(testKey).isNotNull
  }

  @Test
  fun testImportMacKey() {
    val keyHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val injector = getInjector(listOf(Pair("test-mac", keyHandle)))
    val mac = injector.getInstance(MacKeyManager::class.java)["test-mac"]
    assertThat(mac).isNotNull
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
  fun testImportHybridKey() {
    val keyHandle = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
    val injector = getInjector(listOf(Pair("test-hybrid", keyHandle)))
    val hybridEncryptKeyManager = injector.getInstance(HybridEncryptKeyManager::class.java)
    val hybridDecryptKeyManager = injector.getInstance(HybridDecryptKeyManager::class.java)
    assertThat(hybridEncryptKeyManager).isNotNull
    assertThat(hybridDecryptKeyManager).isNotNull

    val plaintext = "plaintext".toByteArray()
    val encrypter = hybridEncryptKeyManager["test-hybrid"]
    assertThat(encrypter).isNotNull
    val ciphertext = encrypter.encrypt(plaintext, null)

    val decrypter = hybridDecryptKeyManager["test-hybrid"]
    assertThat(decrypter).isNotNull
    val decrypted = decrypter.decrypt(ciphertext, null)

    assertThat(plaintext).isEqualTo(decrypted)
  }

  @Test
  fun testImportOnlyPublicHybridKey() {
    val keysetHandle = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM).publicKeysetHandle
    val injector = getInjector(listOf(Pair("test-hybrid", keysetHandle)))
    val hybridEncryptKeyManager = injector.getInstance(HybridEncryptKeyManager::class.java)
    assertThat(hybridEncryptKeyManager).isNotNull
    assertThat(hybridEncryptKeyManager["test-hybrid"]).isNotNull
  }

  @Test
  fun testMultipleKeys() {
    val aeadHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val macHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val injector = getInjector(listOf(Pair("aead", aeadHandle), Pair("mac", macHandle)))
    assertThat(injector.getInstance(AeadKeyManager::class.java)["aead"]).isNotNull
    assertThat(injector.getInstance(MacKeyManager::class.java)["mac"]).isNotNull
  }

  @Test
  fun testDuplicateNames() {
    getInjector(listOf()) // Call without args first to initialize primitives
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
    assertThatThrownBy { injector.getInstance(AeadKeyManager::class.java)["not there either"] }
            .isInstanceOf(KeyNotFoundException::class.java)
  }

  @Test
  fun testLogsWarningWithoutEnvelopeKey() {
    val kh = KeysetHandle.generateNew(AeadKeyTemplates.AES256_CTR_HMAC_SHA256)
    val name = "name"
    val encryptedKey = generateObsoleteEncryptedKey(kh)
    val key = Key("name", KeyType.AEAD, encryptedKey, "aws-kms://some-uri")
    val injector = getInjectorWithKeys(listOf(key))
    val lcs = injector.getInstance(LogCollectorService::class.java)
    lcs.startAsync()
    lcs.awaitRunning()

    val lc = injector.getInstance(LogCollector::class.java)

    val kr = injector.getInstance(KeyReader::class.java)
    kr.readKey(name)
    val out = lc.takeMessage()
    assertThat(out).contains("using obsolete key format")
  }

  @Test
  fun testAeadExtensionFunctions() {
    val keyHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val testKey = injector.getInstance(AeadKeyManager::class.java)["test"]

    // test with encryption context
    var encryptionContext: ByteArray? = byteArrayOf(1, 2, 3, 4)
    val plaintext = "Hello world!".toByteArray(Charsets.UTF_8)
    var ciphertext = testKey.encrypt(plaintext, encryptionContext)
    assertThat(testKey.decrypt(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThatThrownBy { testKey.decrypt(ciphertext, byteArrayOf()) }
        .hasMessage("decryption failed")
    assertThatThrownBy { testKey.decrypt(ciphertext, null) }
        .hasMessage("decryption failed")
    assertThatThrownBy { testKey.decrypt(ciphertext, byteArrayOf(4, 3, 2, 1)) }
        .hasMessage("decryption failed")

    // test with empty encryption context
    encryptionContext = byteArrayOf()
    ciphertext = testKey.encrypt(plaintext, encryptionContext)
    assertThat(testKey.decrypt(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThat(testKey.decrypt(ciphertext, null)).isEqualTo(plaintext)
    assertThatThrownBy { testKey.decrypt(ciphertext, byteArrayOf(4, 3, 2, 1)) }
        .hasMessage("decryption failed")

    // test with no encryption context
    encryptionContext = null
    ciphertext = testKey.encrypt(plaintext, encryptionContext)
    assertThat(testKey.decrypt(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThat(testKey.decrypt(ciphertext, byteArrayOf())).isEqualTo(plaintext)
    assertThatThrownBy { testKey.decrypt(ciphertext, byteArrayOf(4, 3, 2, 1)) }
        .hasMessage("decryption failed")
  }

  @Test
  fun testDaeadExtensionFunctions() {
    val keyHandle = KeysetHandle.generateNew(DeterministicAeadKeyTemplates.AES256_SIV)
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val daead = injector.getInstance(DeterministicAeadKeyManager::class.java)["test"]
    val plaintext = "plaintext".toByteArray(Charsets.UTF_8)
    // with encryption context
    var encryptionContext: ByteArray? = byteArrayOf(1, 2, 3, 4)
    var ciphertext = daead.encryptDeterministically(plaintext, encryptionContext)
    assertThat(daead.decryptDeterministically(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThatThrownBy { daead.decryptDeterministically(ciphertext, byteArrayOf())}
        .hasMessage("decryption failed")
    assertThatThrownBy { daead.decryptDeterministically(ciphertext, byteArrayOf(4, 3, 2, 1))}
        .hasMessage("decryption failed")

    // test with empty encryption context
    encryptionContext = byteArrayOf()
    ciphertext = daead.encryptDeterministically(plaintext, encryptionContext)
    assertThat(daead.decryptDeterministically(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThatThrownBy { daead.decryptDeterministically(ciphertext, byteArrayOf(4, 3, 2, 1)) }
        .hasMessage("decryption failed")
  }

  @Test
  fun testHybridExtensionMethods() {
    val keyHandle = KeysetHandle.generateNew(HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val hybridEncrypt = injector.getInstance(HybridEncryptKeyManager::class.java)["test"]
    val hybridDecrypt = injector.getInstance(HybridDecryptKeyManager::class.java)["test"]
    val plaintext = "plaintext".toByteArray(Charsets.UTF_8)
    // with encryption context
    var encryptionContext: ByteArray? = byteArrayOf(1, 2, 3, 4)
    var ciphertext = hybridEncrypt.encrypt(plaintext, encryptionContext)
    assertThat(hybridDecrypt.decrypt(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThatThrownBy { hybridDecrypt.decrypt(ciphertext, byteArrayOf())}
        .hasMessage("decryption failed")
    assertThatThrownBy { hybridDecrypt.decrypt(ciphertext, null)}
        .hasMessage("decryption failed")
    assertThatThrownBy { hybridDecrypt.decrypt(ciphertext, byteArrayOf(4, 3, 2, 1))}
        .hasMessage("decryption failed")

    // test with empty encryption context
    encryptionContext = byteArrayOf()
    ciphertext = hybridEncrypt.encrypt(plaintext, encryptionContext)
    assertThat(hybridDecrypt.decrypt(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThat(hybridDecrypt.decrypt(ciphertext, null)).isEqualTo(plaintext)
    assertThatThrownBy { hybridDecrypt.decrypt(ciphertext, byteArrayOf(4, 3, 2, 1)) }
        .hasMessage("decryption failed")

    // test with no encryption context
    encryptionContext = null
    ciphertext = hybridEncrypt.encrypt(plaintext, encryptionContext)
    assertThat(hybridDecrypt.decrypt(ciphertext, encryptionContext)).isEqualTo(plaintext)
    assertThat(hybridDecrypt.decrypt(ciphertext, byteArrayOf())).isEqualTo(plaintext)
    assertThatThrownBy { hybridDecrypt.decrypt(ciphertext, byteArrayOf(4, 3, 2, 1)) }
        .hasMessage("decryption failed")
  }

  @Test
  fun testMacExtensionMethods() {
    val keyHandle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_256BITTAG)
    val injector = getInjector(listOf(Pair("test", keyHandle)))
    val hmac = injector.getInstance(MacKeyManager::class.java)["test"]
    val message = "hello!"
    val tag = hmac.computeMac(message)
    assertThatCode { hmac.verifyMac(tag, message) }.doesNotThrowAnyException()
    assertThatThrownBy { hmac.verifyMac("not a base64 string", message) }
        .hasMessageContaining("invalid tag:")
    assertThatThrownBy {
      hmac.verifyMac(Base64.getEncoder().encodeToString("wrong tag!".toByteArray()), message)
    }.hasMessage("invalid MAC")
  }

  @Test
  fun testStreamingAead() {
    val keysetHandle = KeysetHandle.generateNew(StreamingAeadKeyTemplates.AES256_GCM_HKDF_4KB)
    val injector = getInjector(listOf(Pair("test", keysetHandle)))
    val streamingAead = injector.getInstance(StreamingAeadKeyManager::class.java)["test"]
    val aad = byteArrayOf(1, 2, 3, 4)

    val encryptedBytesOutput = ByteArrayOutputStream()
    val ciphertextOutput = streamingAead.newEncryptingStream(encryptedBytesOutput, aad)
    ciphertextOutput.write("this is a very ".toByteArray(Charsets.UTF_8))
    ciphertextOutput.write("very very very ".toByteArray(Charsets.UTF_8))
    ciphertextOutput.write("long message".toByteArray(Charsets.UTF_8))
    ciphertextOutput.close()
    val ciphertext = encryptedBytesOutput.toByteArray()
    assertThat(ciphertext).isNotEmpty
    assertThat(ciphertext.toByteString().utf8()).doesNotContain("long message")

    var decrypted = byteArrayOf()
    val decryptionByteArray = ByteArrayInputStream(ciphertext)
    val decryptionStream = streamingAead.newDecryptingStream(decryptionByteArray, aad)
    val buffer = ByteArray(8)
    var readBytes = decryptionStream.read(buffer)
    while (readBytes > 0) {
      decrypted = decrypted.plus(buffer.copyOfRange(0, readBytes))
      buffer.fill(0)
      readBytes = decryptionStream.read(buffer)
    }
    decryptionStream.close()
    assertThat(decrypted).isNotEmpty
    assertThat(decrypted.toByteString().utf8()).contains("long message")
  }

  @Test
  fun testBasicExternal() {
    val name = "extern"
    val injector = getInjector(listOf(), mapOf(name to KeyType.DAEAD))
    val kr = injector.getInstance(KeyReader::class.java)
    assertThat(kr.readKey(name)).isNotNull
  }

  @Disabled
  @Test // Currently disabled since the env check is as well
  fun testRaisesInWrongEnv() {
    val injector = getInjector(emptyList())
    val plainKey = Key("name", KeyType.AEAD, MiskConfig.RealSecret(""))
    val kr = injector.getInstance(KeyReader::class.java)

    assertThatThrownBy {
      // kr.env = Environment.STAGING
      kr.readKey(plainKey.key_name)
    }.isInstanceOf(GeneralSecurityException::class.java)

    assertThatThrownBy {
      // kr.env = Environment.PRODUCTION
      kr.readKey(plainKey.key_name)
    }.isInstanceOf(GeneralSecurityException::class.java)
  }

  private fun getInjector(keyMap: List<Pair<String, KeysetHandle>>, external: Map<KeyAlias, KeyType>? = null): Injector {
    val keys = keyMap.map {
      var keyType = KeyType.AEAD
      val keyTypeUrl = it.second.keysetInfo.getKeyInfo(0).typeUrl
      if (keyTypeUrl.endsWith("HmacKey")) {
        keyType = KeyType.MAC
      } else if (keyTypeUrl.endsWith("Ed25519PrivateKey")) {
        keyType = KeyType.DIGITAL_SIGNATURE
      } else if (keyTypeUrl.endsWith("EciesAeadHkdfPrivateKey")) {
        keyType = KeyType.HYBRID_ENCRYPT_DECRYPT
      } else if (keyTypeUrl.endsWith("EciesAeadHkdfPublicKey")) {
        keyType = KeyType.HYBRID_ENCRYPT
      } else if (keyTypeUrl.endsWith("AesSivKey")) {
        keyType = KeyType.DAEAD
      } else if (keyTypeUrl.endsWith("AesGcmHkdfStreamingKey")) {
        keyType = KeyType.STREAMING_AEAD
      }
      Key(it.first, keyType, generateEncryptedKey(it.second))
    }
    val config = CryptoConfig(keys, "test_master_key", external.orEmpty())
    return Guice.createInjector(DeploymentModule.forTesting(), CryptoTestModule(config))
  }

  private fun getInjectorWithKeys(keys: List<Key>): Injector {
    val config = CryptoConfig(keys, "test_master_key", mapOf())
    return Guice.createInjector(DeploymentModule.forTesting(), CryptoTestModule(config))
  }
  private fun generateEncryptedKey(keyHandle: KeysetHandle): Secret<String> {
    val masterKey = FakeMasterEncryptionKey()
    val keyOutputStream = ByteArrayOutputStream()
    val kek = KmsEnvelopeAead(KeyReader.KEK_TEMPLATE, masterKey)
    keyHandle.write(JsonKeysetWriter.withOutputStream(keyOutputStream), kek)
    return object : Secret<String> {
      override val value: String
        get() = keyOutputStream.toString()
    }
  }

  private fun generateObsoleteEncryptedKey(keyHandle: KeysetHandle): Secret<String> {
    val masterKey = FakeMasterEncryptionKey()
    val keyOutputStream = ByteArrayOutputStream()
    keyHandle.write(JsonKeysetWriter.withOutputStream(keyOutputStream), masterKey)
    return object : Secret<String> {
      override val value: String
        get() = keyOutputStream.toString()
    }
  }
}
