package misk.crypto

import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.Mac
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.StreamingAead
import com.google.inject.ConfigurationException
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.name.Names
import java.security.GeneralSecurityException
import javax.inject.Singleton

sealed class MappedKeyManager<KeyT> constructor(
  private val injector: Injector,
  private val keyClass: Class<KeyT>
) {
  private val keys: HashMap<String, KeyT> = LinkedHashMap()

  internal operator fun set(name: String, k: KeyT) = keys.set(name, k)

  operator fun get(name: String): KeyT =
      keys.getOrPut(name) { getKeyInstance(name) }

  protected fun <T> getNamedInstance(klass: Class<T>, name: String): T {
    try {
      return injector.getInstance(Key.get(klass, Names.named(name)))
    } catch (ex: ConfigurationException) {
      throw KeyNotFoundException("key '$name' could not be found", ex)
    }
  }

  internal open fun getKeyInstance(name: String): KeyT =
      getNamedInstance(keyClass, name)
}

/**
 * Holds a map of every [Aead] key name to its primitive listed in the configuration for this app.
 *
 * Users may use this object to obtain an [Aead] dynamically:
 * ```
 * val myKey: Aead = aeadKeyManager["myKey"]
 * ```
 * Note: [Aead] instances provided by this module are **envelope Aead** instances.
 * This means that all data is encrypted with an ephemeral data encryption key (DEK),
 * which is then protected by a key-encryption key (KEK) and stored inline with ciphertext.
 * This effectively means that ciphertext will be a bit larger than the plaintext,
 * and that migrating keys (KEKs) should not require the re-encryption of stored data.
 */
@Singleton
class AeadKeyManager @Inject internal constructor(
  injector: Injector
) : MappedKeyManager<Aead>(injector, Aead::class.java)

/**
 * Holds a map of every [DeterministicAead] key name to its primitive listed in the configuration for this app.
 *
 * Users may use this object to obtain an [DeterministicAead] dynamically:
 * ```
 * val myKey: DeterministicAead = deterministicAeadKeyManager["myKey"]
 * ```
 * Note that DeterministicAead objects do not provide secrecy to the same level as AEAD do, since
 * multiple plaintexts encrypted with the same key will produce identical ciphertext. This behavior
 * is desirable when querying data via its ciphertext (i.e. equality will hold), but an attacker can
 * detect repeated plaintexts.
 */
@Singleton
class DeterministicAeadKeyManager @Inject internal constructor(
  injector: Injector
) : MappedKeyManager<DeterministicAead>(injector, DeterministicAead::class.java)

/**
 * Holds a map of every [Mac] key name to its primitive listed in the configuration for this app.
 *
 * Users may use this class to obtain a [Mac] object dynamically:
 * ```
 * val hmac: Mac = macKeyManager["myHmac"]
 * ```
 */
@Singleton
class MacKeyManager @Inject internal constructor(
  injector: Injector
) : MappedKeyManager<Mac>(injector, Mac::class.java)

/**
 * Holds a map of every key name to its corresponding [PublicKeySign] and [PublicKeyVerify] primitives.
 *
 * Users may use this class to obtain a [PublicKeySign] to sign data
 * or [PublicKeyVerify] to verify the integrity of a message dynamically:
 * ```
 * val signer: PublicKeySign = keyManager.getSigner("myDigitalSignatureKey")
 * val verifier: PublicKeyVerify = keyManager.getVerifier("mySigitalSignatureKey")
 * val signature = signer.sign(data)
 * verifier.verify(signature, data)
 * ```
 */
@Singleton
class DigitalSignatureKeyManager @Inject internal constructor(
  injector: Injector
) : MappedKeyManager<DigitalSignature>(injector, DigitalSignature::class.java) {

  fun getSigner(name: String): PublicKeySign = this[name].signer
  fun getVerifier(name: String): PublicKeyVerify = this[name].verifier

  override fun getKeyInstance(name: String): DigitalSignature {
    val signer = getNamedInstance(PublicKeySign::class.java, name)
    val verifier = getNamedInstance(PublicKeyVerify::class.java, name)
    return DigitalSignature(signer, verifier)
  }
}

data class DigitalSignature(val signer: PublicKeySign, val verifier: PublicKeyVerify)

/**
 * Holds a map of every [HybridEncrypt] key name to its corresponding primitive listed
 * in the configuration for this app.
 *
 * Users may use this class to obtain a [HybridEncrypt] object dynamically:
 * ```
 * val hybridEncrypt: HybridEncrypt = hybridKeyManager["myHybridKey"]
 * ```
 * Note: Hybrid encryption is intentionally divided to 2 separate key managers,
 * [HybridEncryptKeyManager] and [HybridDecryptKeyManager], so that the public portion of the keyset
 * could be exported to other services.
 * This configuration helps achieve the goal of allowing some services to **encrypt only**
 * and other services to both encrypt and decrypt.
 */
@Singleton
class HybridEncryptKeyManager @Inject internal constructor(
  injector: Injector
) : MappedKeyManager<HybridEncrypt>(injector, HybridEncrypt::class.java)

/**
 * Holds a map of every [HybridDecrypt] key name to its corresponding primitive listed
 * in the configuration for this app.
 *
 * Users may this class to obtain a [HybridDecrypt] object dynamically:
 * ```
 * val hybridDecrypt: HybridDecrypt = hybridKeyManager["myHybridKey"]
 * ```
 */
@Singleton
class HybridDecryptKeyManager @Inject internal constructor(
  injector: Injector
) : MappedKeyManager<HybridDecrypt>(injector, HybridDecrypt::class.java)

class KeyNotFoundException(
  message: String? = null,
  cause: Throwable? = null
) : GeneralSecurityException(message, cause)

/**
 * Holds a map of every [StreamingAead] key name to its primitive listed in the configuration for this app.
 *
 * Users may use this object to obtain an [StreamingAead] dynamically:
 * ```
 * val myKey: StreamingAead = streamingAeadKeyManager["myKey"]
 * ```
 * Note: [StreamingAead] is useful when the data to be encrypted is too large to be processed in a single step.
 * Typical use cases include encryption of large files or encryption of live data streams
 */
@Singleton
class StreamingAeadKeyManager @Inject constructor(
  injector: Injector
) : MappedKeyManager<StreamingAead>(injector, StreamingAead::class.java)
