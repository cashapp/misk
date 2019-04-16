package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.Mac
import com.google.inject.ConfigurationException
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.name.Names
import javax.inject.Singleton

/**
 * Singleton class used as a map of key name to its corresponding [Cipher] implementation.
 */
@Singleton
class KeyManager @Inject constructor(
  private val injector: Injector
) {

  private var keys: HashMap<String, Any> = LinkedHashMap()

  internal operator fun set(name: String, key: Any) {
    keys[name] = key
  }

  operator fun <T> get(name: String): T? {
    var key: Any?
    key = keys[name]
    if (key == null) {
      try {
        key = injector.getInstance(Key.get(Aead::class.java, Names.named(name)))
        keys[name] = key
      } catch (e: ConfigurationException) {
      }
      if (key == null) {
        try {
          key = injector.getInstance(Key.get(Mac::class.java, Names.named(name)))
          keys[name] = key
        } catch (e: ConfigurationException) {
        }
      }
    }
    @Suppress("UNCHECKED_CAST")
    return key as T?
  }
}