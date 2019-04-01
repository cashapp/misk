package misk.crypto

import com.google.inject.Inject
import javax.inject.Singleton

/**
 * Singleton class used as a map of key name to its corresponding [Cipher] implementation.
 */
@Singleton
class KeyManager @Inject constructor() {

  private var keys: HashMap<String, Cipher> = LinkedHashMap()

  internal operator fun set(name: String, cipher: Cipher) {
    keys[name] = cipher
  }

  operator fun get(name: String): Cipher? {
    return keys[name]
  }
}