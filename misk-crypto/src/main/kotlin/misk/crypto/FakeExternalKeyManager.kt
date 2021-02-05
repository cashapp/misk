package misk.crypto

import misk.config.MiskConfig
import java.lang.UnsupportedOperationException

class FakeExternalKeyManager : ExternalKeyManager {
  private var returnedKeysets: MutableMap<KeyAlias, Key> = mutableMapOf()

  override val allKeyAliases: Map<KeyAlias, KeyType>
    get() = returnedKeysets.map { it.key to it.value.key_type }.toMap()

  // Mock remote keys
  constructor(aliases: Map<KeyAlias, KeyType>) {
    aliases.forEach { (alias, type) ->
      if (type == KeyType.PGP_ENCRYPT || type == KeyType.PGP_DECRYPT) {
        throw UnsupportedOperationException("PGP keys are not supported yet as external keys")
      }
      val secret = keyTypeToSecret(type)
      returnedKeysets[alias] = Key(alias, type, secret)
    }
  }

  // Mock local keys
  constructor(rawKeys: List<Key>) {
    rawKeys.forEach { key ->
      val plaintextSecret = keyTypeToSecret(key.key_type)
      val encryptedSecret = if ((key.key_type == KeyType.HYBRID_ENCRYPT && key.kms_uri == null) ||
              key.key_type == KeyType.PGP_DECRYPT || key.key_type == KeyType.PGP_ENCRYPT) {
        plaintextSecret
      } else if (key.key_name == "obsolete") {
        key.encrypted_key
      } else {
        TestKeysets.encryptSecret(plaintextSecret)
      }
      returnedKeysets[key.key_name] = key.copy(encrypted_key = encryptedSecret)
    }
  }

  private fun keyTypeToSecret(type: KeyType): MiskConfig.RealSecret<String> {
    return when (type) {
      KeyType.AEAD -> TestKeysets.AEAD
      KeyType.DAEAD -> TestKeysets.DAEAD
      KeyType.STREAMING_AEAD -> TestKeysets.STREAMING_AEAD
      KeyType.MAC -> TestKeysets.MAC
      KeyType.DIGITAL_SIGNATURE -> TestKeysets.DIGITAL_SIGNATURE
      KeyType.HYBRID_ENCRYPT, KeyType.HYBRID_ENCRYPT_DECRYPT ->
        TestKeysets.HYBRID
      KeyType.PGP_ENCRYPT -> TestKeysets.PGP_ENCRYPT
      KeyType.PGP_DECRYPT -> TestKeysets.PGP_DECRYPT
    }
  }

  override fun getKeyByAlias(alias: KeyAlias): Key? {
    return returnedKeysets[alias]
  }

}
