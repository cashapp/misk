package misk.crypto

import com.google.crypto.tink.KeysetHandle

/**
 * [LocalConfigKeyProvider] provides keys that are stored locally and proetcted by a single KMS
 * key.
 */
class LocalConfigKeyProvider(
  private val keys: List<Key>,
  private val kmsUri: String
) : ExternalKeyManager {

  override val allKeyAliases: Map<KeyAlias, KeyType> =
      keys.map { key -> key.key_name to key.key_type }.toMap()

  override fun getKeyByAlias(alias: KeyAlias): Key? {
    return keys.find { key -> key.key_name == alias }?.copy(kms_uri = kmsUri)
  }

  override fun onKeyUpdated(cb: (KeyAlias, KeysetHandle) -> Unit) = false

}