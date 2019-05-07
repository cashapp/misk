package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.mac.MacConfig
import com.google.crypto.tink.aead.KmsEnvelopeAead
import com.google.crypto.tink.mac.MacFactory
import com.google.crypto.tink.signature.PublicKeySignFactory
import com.google.crypto.tink.signature.PublicKeyVerifyFactory
import com.google.crypto.tink.signature.SignatureConfig
import com.google.inject.name.Names
import misk.inject.KAbstractModule
import javax.inject.Inject
import javax.inject.Provider

/**
 * This module should be used for testing purposes only.
 * It uses the hardcoded keys defined in [TEST_AEAD_KEYSET], [TEST_MAC_KEYSET] and [TEST_SIGNATURE_KEYSET]
 * and uses [FakeKmsClient] instead of a real KMS service.
 *
 * This module **will** read the exact same configuration files as the real application,
 * but **will not** use the key material specified in the configuration.
 * Instead, it'll generate a predefined [KeysetHandle] for each named key.
 */
class CryptoTestModule(
  private val keyNames: List<Key>
) : KAbstractModule() {

  private companion object {
    /**
     * Fake master key to be used instead of a real [com.google.crypto.tink.KmsClient]
     */
    val masterKey = FakeMasterEncryptionKey()
    /**
     * Hardcoded [Aead] [KeysetHandle] that's loaded and used by [AeadProvider]
     */
    const val TEST_AEAD_KEYSET = "{\n" +
        "    \"keysetInfo\": {\n" +
        "        \"primaryKeyId\": 1739368145,\n" +
        "        \"keyInfo\": [{\n" +
        "            \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\",\n" +
        "            \"outputPrefixType\": \"TINK\",\n" +
        "            \"keyId\": 1739368145,\n" +
        "            \"status\": \"ENABLED\"\n" +
        "        }]\n" +
        "    },\n" +
        "    \"encryptedKeyset\": \"Q05ITnNyMEdFbVFLV0Fvd2RIbHdaUzVuYjI5bmJHVmhjR2x6TG1OdmJTOW5iMjluYkdVdVkzSjVjSFJ2TG5ScGJtc3VRV1Z6UjJOdFMyVjVFaUlhSUtjNWVJdkxZRUp1Y2RROTFNQjU0RWc5dVo3cTg2ZzM5aWZhQUhveWhIVTVHQUVRQVJqUnpiSzlCaUFC\"\n" +
        "}"
    /**
     * Hardcoded [Mac] [KeysetHandle] that's loaded and used by [MacProvider]
     */
    const val TEST_MAC_KEYSET = "{\n" +
        "    \"keysetInfo\": {\n" +
        "        \"primaryKeyId\": 785905445,\n" +
        "        \"keyInfo\": [{\n" +
        "            \"typeUrl\": \"type.googleapis.com/google.crypto.tink.HmacKey\",\n" +
        "            \"outputPrefixType\": \"TINK\",\n" +
        "            \"keyId\": 785905445,\n" +
        "            \"status\": \"ENABLED\"\n" +
        "        }]\n" +
        "    },\n" +
        "    \"encryptedKeyset\": \"Q0tYdTMvWUNFbWdLWEFvdWRIbHdaUzVuYjI5bmJHVmhjR2x6TG1OdmJTOW5iMjluYkdVdVkzSjVjSFJ2TG5ScGJtc3VTRzFoWTB0bGVSSW9FZ1FJQXhBZ0dpQ2lJbVNYM21mMU1PQUhuMjh6TVoveG5aczNBWkUydFNTQTFuRFBEVjJjaHhnQkVBRVlwZTdmOWdJZ0FRPT0=\"\n" +
        "}"
    /**
     * Hardcoded digital signature [KeysetHandle] that's loaded and used by [DigitalSignatureSignerProvider]
     */
    const val TEST_SIGNATURE_KEYSET = "{\n" +
        "    \"keysetInfo\": {\n" +
        "        \"primaryKeyId\": 582162020,\n" +
        "        \"keyInfo\": [{\n" +
        "            \"typeUrl\": \"type.googleapis.com/google.crypto.tink.Ed25519PrivateKey\",\n" +
        "            \"outputPrefixType\": \"TINK\",\n" +
        "            \"keyId\": 582162020,\n" +
        "            \"status\": \"ENABLED\"\n" +
        "        }]\n" +
        "    },\n" +
        "    \"encryptedKeyset\": \"Q09Tc3pKVUNFcEVCQ29RQkNqaDBlWEJsTG1kdmIyZHNaV0Z3YVhNdVkyOXRMMmR2YjJkc1pTNWpjbmx3ZEc4dWRHbHVheTVGWkRJMU5URTVVSEpwZG1GMFpVdGxlUkpHRWlCZzBBUTd6QzJVT2IrN2pPKzJYUTZtT1IyYTB1ZlAzV1pkZjdVZk9zWHdpUm9pRWlCVmFsQUwrdFB5bG01NlluLzNzNTZqVGluY1V6bXoycGV6c0JwZnRlOUMzUmdDRUFFWTVLek1sUUlnQVE9PQ==\"\n" +
        "}"
  }

  override fun configure() {
    AeadConfig.register()
    MacConfig.register()
    SignatureConfig.register()

    keyNames.forEach { key ->
      when(key.key_type) {
        KeyType.AEAD -> {
          bind<Aead>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(AeadProvider(key.key_name))
              .asEagerSingleton()
        }
        KeyType.MAC -> {
          bind<Mac>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(MacProvider(key.key_name))
              .asEagerSingleton()
        }
        KeyType.DIGITAL_SIGNATURE -> {
          val keysetHandle = KeysetHandle.read(JsonKeysetReader.withString(TEST_SIGNATURE_KEYSET), masterKey)
          val signer = PublicKeySignFactory.getPrimitive(keysetHandle)
          val verifier = PublicKeyVerifyFactory.getPrimitive(keysetHandle.publicKeysetHandle)
          bind<PublicKeySign>()
              .annotatedWith(Names.named(key.key_name))
              .toProvider(DigitalSignatureSignerProvider(signer, verifier, key.key_name))
              .asEagerSingleton()
          bind<PublicKeyVerify>()
              .annotatedWith(Names.named(key.key_name))
              .toInstance(verifier)
        }
      }
    }
  }

  private class AeadProvider(val keyName: String) : Provider<Aead> {
    @Inject lateinit var keyManager: AeadKeyManager

    override fun get(): Aead {
      val keysetHandle = KeysetHandle.read(JsonKeysetReader.withString(TEST_AEAD_KEYSET), masterKey)
      val kek = AeadFactory.getPrimitive(keysetHandle)
      val envelopeKey = KmsEnvelopeAead(AeadKeyTemplates.AES128_GCM, kek)
      return envelopeKey.also { keyManager[keyName] = it }
    }
  }

  private class MacProvider(val keyName: String) : Provider<Mac> {
    @Inject lateinit var keyManager: MacKeyManager

    override fun get(): Mac {
      val keysetHandle = KeysetHandle.read(JsonKeysetReader.withString(TEST_MAC_KEYSET), masterKey)
      return MacFactory.getPrimitive(keysetHandle)
          .also { keyManager[keyName] = it }
    }
  }

  private class DigitalSignatureSignerProvider(val signer: PublicKeySign,
    val verifier: PublicKeyVerify, val name: String) : Provider<PublicKeySign> {
    @Inject lateinit var keyManager: DigitalSignatureKeyManager

    override fun get(): PublicKeySign {
      keyManager[name] = DigitalSignature(signer, verifier)
      return signer
    }
  }
}