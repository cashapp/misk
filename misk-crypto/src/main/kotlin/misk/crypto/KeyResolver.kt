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
 * [KeyResolver] provides an interface to access keys indexed by aliases.
 * Optionally, callers can register a callback to be invoked when a key is updated.
 */
interface KeyResolver {
  /**
   * All resolved key aliases and their respective KeyTypes.
   */
  val allKeyAliases: Map<KeyAlias, KeyType>

  /**
   * Fetch and return a Key (includes contents, type and a KMS ARN) by its alias.
   */
  fun getKeyByAlias(alias: KeyAlias): Key?
}
