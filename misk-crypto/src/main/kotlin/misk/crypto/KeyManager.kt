package misk.crypto

import javax.inject.Singleton

/**
 * Singleton class used as a map of key name to its corresponding [Cipher] implementation.
 * It's being populated by the [CryptoModule] class when loaded.
 */
@Singleton
class KeyManager {

  private var keys: HashMap<String, Cipher> = LinkedHashMap()

  internal operator fun set(name: String, cipher: Cipher) {
    keys[name] = cipher
  }

  operator fun get(name: String): Cipher? {
    return keys[name]
  }
}