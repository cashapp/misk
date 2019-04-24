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
 * Holds a map of every [Aead] key name to its primitive listed in the configuration for this app.
 * Users may use this object to obtain an [Aead] dynamically:
 * ```
 * val myKey: Aead? = aeadKeyManager["myKey"]
 * ```
 */
@Singleton
class AeadKeyManager @Inject internal constructor(
  private val injector: Injector
) {
  private val aeads: HashMap<String, Aead> = LinkedHashMap()

  internal operator fun set(name: String, aead: Aead) {
    aeads[name] = aead
  }

  operator fun get(name: String): Aead? {
    var aead = aeads[name]
    if (aead == null) {
      try {
        aead = injector.getInstance(Key.get(Aead::class.java, Names.named(name)))
        this[name] = aead
      } catch (e: ConfigurationException) {
      }
    }
    return aead
  }
}

/**
 * Holds a map of every [Mac] key name to its primitive listed in the configuration for this app.
 * Users may use htis objecy to obtain a [Mac] object dynamically:
 * ```
 * val hmac: Mac? = macKeyManager["myHmac"]
 * ```
 */
@Singleton
class MacKeyManager @Inject internal constructor(
  private val injector: Injector
) {
  private val macs: HashMap<String, Mac> = LinkedHashMap()

  internal operator fun set(name: String, mac: Mac) {
    macs[name] = mac
  }

  operator fun get(name: String): Mac? {
    var mac = macs[name]
    if (mac == null) {
      try {
        mac = injector.getInstance(Key.get(Mac::class.java, Names.named(name)))
        this[name] = mac
      } catch (e: ConfigurationException) {
      }
    }
    return mac
  }
}