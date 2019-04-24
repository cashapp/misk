package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
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
 * Users may use this class to obtain a [Mac] object dynamically:
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

/**
 * Holds a map of every key name to its corresponding [PublicKeySign] and [PublicKeyVerify] primitives.
 * Users may use this class to obtain a [PublicKeySign] to sign data
 * or [PublicKeyVerify] to verify the integrity of a message dynamically:
 * ```
 * val signer: PublicKeySign? = keyManager.getSigner("myDigitalSignatureKey")
 * val verifier: PublicKeyVerify? = keyManager.getVerifier("mySigitalSignatureKey")
 * val signature = signer.sign(data)
 * verifier.verify(signature, data)
 * ```
 */
@Singleton
class DigitalSignatureKeyManager @Inject internal constructor(
  private val injector: Injector
) {
  private val signers: HashMap<String, DigitalSignature> = LinkedHashMap()

  internal operator fun set(name: String, signerAndVerifier: DigitalSignature) {
    if (!signers.containsKey(name)) {
      signers[name] = signerAndVerifier
    }
  }

  fun getSigner(name: String): PublicKeySign? {
    var signer = signers[name]?.signer
    if (signer == null) {
      try {
        signer = injector.getInstance(Key.get(PublicKeySign::class.java, Names.named(name)))
        val verifier = injector.getInstance(Key.get(PublicKeyVerify::class.java, Names.named(name)))
        this[name] = DigitalSignature(signer, verifier)
      } catch (e: ConfigurationException) {
      }
    }
    return signer
  }

  fun getVerifier(name: String): PublicKeyVerify? {
    var verifier = signers[name]?.verifier
    if (verifier == null) {
      try {
        verifier = injector.getInstance(Key.get(PublicKeyVerify::class.java, Names.named(name)))
        val signer = injector.getInstance(Key.get(PublicKeySign::class.java, Names.named(name)))
        this[name] = DigitalSignature(signer, verifier)
      } catch (e: ConfigurationException) {
      }
    }
    return verifier
  }
  data class DigitalSignature(val signer: PublicKeySign, val verifier: PublicKeyVerify)
}

