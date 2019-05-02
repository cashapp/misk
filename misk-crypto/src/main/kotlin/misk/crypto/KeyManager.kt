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
 * Users may use this object to obtain an [Aead] dynamically:
 * ```
 * val myKey: Aead? = aeadKeyManager["myKey"]
 * ```
 */
@Singleton
class AeadKeyManager @Inject internal constructor(injector: Injector)
    : MappedKeyManager<Aead>(injector, Aead::class.java)

/**
 * Holds a map of every [Mac] key name to its primitive listed in the configuration for this app.
 * Users may use this class to obtain a [Mac] object dynamically:
 * ```
 * val hmac: Mac? = macKeyManager["myHmac"]
 * ```
 */
@Singleton
class MacKeyManager @Inject internal constructor(injector: Injector)
    : MappedKeyManager<Mac>(injector, Mac::class.java)


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
class DigitalSignatureKeyManager @Inject internal constructor(injector: Injector)
    : MappedKeyManager<DigitalSignature>(injector, DigitalSignature::class.java) {

    fun getSigner(name: String): PublicKeySign = this[name].signer
    fun getVerifier(name: String): PublicKeyVerify = this[name].verifier

    override fun getKeyInstance(name: String): DigitalSignature {
        val signer = getNamedInstance(PublicKeySign::class.java, name)
        val verifier = getNamedInstance(PublicKeyVerify::class.java, name)
        return DigitalSignature(signer, verifier)
    }
}


data class DigitalSignature(val signer: PublicKeySign, val verifier: PublicKeyVerify)

class KeyNotFoundException(
        message: String? = null,
        cause: Throwable? = null
) : GeneralSecurityException(message, cause)