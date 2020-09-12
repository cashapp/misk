package misk.crypto

import com.google.crypto.tink.KeysetHandle
import java.io.IOException

/**
 * How we refer to a Tink keyset
 */
typealias KeyAlias = String

/**
 * Thrown on creation if external key does not exist.
 */
class ExternalKeyManagerException(message: String) : IOException(message)

/**
 * [ExternalKeyManager] provides an interface to access keys in a remote location indexed by a
 * aliases. Optionally, callers can register a callback to invoked when a key is updated.
 */
interface ExternalKeyManager {

  val allKeyAliases: Map<KeyAlias, KeyType>

  /**
   * Fetch and return a Key and its associated KMS ARN by its alias.
   */
  fun getKeyByAlias(alias: KeyAlias): Key?

  /**
   * Register a callback to be invoked when a key is updated, such in the case of key rotations or
   * new keys.
   */
  fun onKeyUpdated(cb: (KeyAlias, KeysetHandle) -> Unit): Boolean
}
