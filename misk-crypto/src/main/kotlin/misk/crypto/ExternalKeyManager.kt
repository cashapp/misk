package misk.crypto

import java.io.IOException

/**
 * A KeyAlias is how we refer to a Tink keyset.
 */
typealias KeyAlias = String

/**
 * Thrown on creation if external key does not exist.
 */
class ExternalKeyManagerException(message: String) : IOException(message)

/**
 * [ExternalKeyManager] provides an interface to access keys in a remote location indexed by
 * aliases. Optionally, callers can register a callback to be invoked when a key is updated.
 */
interface ExternalKeyManager {

  val allKeyAliases: Map<KeyAlias, KeyType>

  /**
   * Fetch and return a Key (includes contents, type and a KMS ARN) by its alias.
   */
  fun getKeyByAlias(alias: KeyAlias): Key?

}
