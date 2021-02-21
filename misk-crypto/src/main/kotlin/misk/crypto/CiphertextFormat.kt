package misk.crypto

import com.google.common.annotations.VisibleForTesting
import com.google.common.io.ByteStreams
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.security.GeneralSecurityException

/**
 * Wraps a ciphertext and the encryption context associated with it in a [ByteArray].
 *
 * Misk uses Tink to encrypt data, which uses Encryption Context (EC),
 * or Additional Authentication Data (AAD) to authenticate ciphertext.
 * This class introduces a new, higher level abstraction, that’ll be used instead of the
 * AAD byte array interfaces Tink exposes to users.
 * The main reasons to do this are:
 * - Preventing the misuse of AAD
 * - Preventing undecipherable ciphertext from being created
 * - Exposing a friendlier user interface
 *
 * ## Encryption Context Specification
 * - `Map<String, String>`
 * - The map must contain at least 1 entry
 * - No blank/empty/null strings in either the map's keys or values
 * - The map is optional, and can be completely omitted from the encryption operation
 * The encryption context will be serialized using the following format:
 * ```
 * [ AAD:
 * [ varint: pair count ]
 *   [ pairs:
 *     ( [ varint: key length ] [ ByteArray: key ]
 *       [ varint: value length ] [ ByteArray: value ]
 *     )*
 *   ]
 * ]
 * ```
 *
 * The final output will be serialized using the following format:
 * ```
 * [ 0xEE: magic+version ]
 * [ varint: AAD length ]
 * [ AAD ]
 * [ tink ciphertext ]
 * ```
 *
 * For the full documentation of the [CiphertextFormat] serialization, read FORMAT.md
 */
class CiphertextFormat private constructor() {

  companion object {
    /**
     * Current version of the encryption packet schema
     */
    const val CURRENT_VERSION = 0xEE

    /**
     * Serializes the given [ciphertext] and associated encryption context to a [ByteArray]
     */
    fun serialize(ciphertext: ByteArray, aad: ByteArray?): ByteArray {
      val outputStream = ByteStreams.newDataOutput()
      outputStream.writeByte(CURRENT_VERSION)
      if (aad == null) {
        outputStream.write(byteArrayOf(0))
      } else {
        outputStream.write(encodeVarInt(aad.size))
        outputStream.write(aad)
      }
      outputStream.write(ciphertext)
      return outputStream.toByteArray()
    }

    /**
     * Extracts the ciphertext and associated authentication data from the [serialized] ByteArray.
     *
     * This method also compares the given [context] to the serialized AAD
     * and will throw an exception if they do not match.
     */
    fun deserialize(
      serialized: ByteArray,
      context: Map<String, String>?
    ): Pair<ByteArray, ByteArray?> {
      val src = DataInputStream(ByteArrayInputStream(serialized))
      val version = src.readByte()
      if (version != CURRENT_VERSION.toByte()) {
        throw InvalidCiphertextFormatException("invalid version: $version")
      }
      val ecSize = decodeVarInt(src)
      val aad = if (ecSize > 0) {
        ByteArray(ecSize)
      } else {
        null
      }?.also { src.readFully(it) }

      val serializedEncryptionContext = serializeEncryptionContext(context)
      if (aad == null && serializedEncryptionContext != null) {
        throw UnexpectedEncryptionContextException()
      } else if (aad != null && serializedEncryptionContext == null) {
        throw MissingEncryptionContextException()
      }
      if (aad != null && !serializedEncryptionContext!!.contentEquals(aad)) {
        throw EncryptionContextMismatchException("encryption context doesn't match")
      }
      val ciphertext = readCiphertext(src)
      return Pair(ciphertext, aad)
    }

    /**
     * Serializes the encryption context to a [ByteArray] so it could be passed to Tink's
     * encryption/decryption methods.
     */
    fun serializeEncryptionContext(context: Map<String, String>?): ByteArray? {
      if (context == null || context.isEmpty()) {
        return null
      }

      val buff = ByteStreams.newDataOutput()
      val contextSize = encodeVarInt(context.size)
      var bytesWritten = 0
      buff.write(contextSize)
      bytesWritten += contextSize.size
      context.toSortedMap(compareBy { it }).forEach { (k, v) ->
        if (k.isEmpty() || v.isEmpty()) {
          throw InvalidEncryptionContextException("empty key or value")
        }
        val key = k.toByteArray(Charsets.UTF_8)
        val value = v.toByteArray(Charsets.UTF_8)
        if (key.size >= Short.MAX_VALUE) {
          throw InvalidEncryptionContextException("key is too long")
        }
        if (value.size >= Short.MAX_VALUE) {
          throw InvalidEncryptionContextException("value is too long")
        }
        val keySize = encodeVarInt(key.size)
        buff.write(keySize)
        bytesWritten += keySize.size
        buff.write(key)
        bytesWritten += key.size
        val valueSize = encodeVarInt(value.size)
        buff.write(valueSize)
        bytesWritten += valueSize.size
        buff.write(value)
        bytesWritten += value.size
        if (bytesWritten >= Short.MAX_VALUE.toInt()) {
          throw InvalidEncryptionContextException("encryption context is too long")
        }
      }
      val aad = buff.toByteArray()
      return aad
    }

    @VisibleForTesting
    internal fun deserializeEncryptionContext(aad: ByteArray?): Map<String, String>? {
      if (aad == null) {
        return null
      }
      val src = DataInputStream(ByteArrayInputStream(aad))
      val entries = decodeVarInt(src)
      if (entries == 0) {
        return null
      }
      return (1..entries).map {
        val keySize = decodeVarInt(src)
        val keyBytes = ByteArray(keySize)
        src.readFully(keyBytes)
        val valueSize = decodeVarInt(src)
        val valueBytes = ByteArray(valueSize)
        src.readFully(valueBytes)
        keyBytes.toString(Charsets.UTF_8) to valueBytes.toString(Charsets.UTF_8)
      }.toMap()
    }

    private fun readCiphertext(src: DataInputStream): ByteArray {
      val ciphertextStream = ByteArrayOutputStream()
      var readByte = src.read()
      while (readByte >= 0) {
        ciphertextStream.write(readByte)
        readByte = src.read()
      }
      return ciphertextStream.toByteArray()
    }

    private const val SEPTET = (1 shl 7) - 1
    private const val HAS_MORE_BIT = 1 shl 7

    private fun encodeVarInt(integer: Int): ByteArray {
      val list = mutableListOf<Byte>()
      var intValue = integer
      var byte = intValue and SEPTET
      while (intValue shr 7 > 0) {
        list.add((byte or HAS_MORE_BIT).toByte())
        intValue = intValue shr 7
        byte = intValue and SEPTET
      }
      list.add(intValue.toByte())

      return list.toByteArray()
    }

    private fun decodeVarInt(src: DataInputStream): Int {
      var byte = src.readByte().toInt()
      var integer = byte and SEPTET
      while (byte and HAS_MORE_BIT > 0) {
        byte = src.readByte().toInt()
        integer += ((byte and SEPTET) shl 7)
      }
      return integer
    }

    /**
     * Extracts the ciphertext and encryption context from the [serialized] ByteArray.
     *
     * This method is meant to be used with field-level-encryption in Hibernate only.
     */
    fun deserializeFleFormat(serialized: ByteArray): Pair<ByteArray, Map<String, String?>> {
      val src = DataInputStream(ByteArrayInputStream(serialized))
      val version = src.readByte().toInt()
      if (version != 1) {
        throw InvalidCiphertextFormatException("invalid version")
      }
      val bitmask = src.readInt()
      if (bitmask > Short.MAX_VALUE) {
        throw InvalidCiphertextFormatException("invalid bitmask")
      }
      var context = mutableMapOf<String, String?>()
      var ciphertext: ByteArray? = null
      if (bitmask != 0) {
        context.putAll(
          ContextKey.values()
            .filter { it.index and bitmask != 0 }
            .map { it.name.toLowerCase() to null }
            .toMap()
        )
      }

      when (src.read()) {
        EntryType.EXPANDED_CONTEXT_DESCRIPTION.type -> {
          val size = src.readUnsignedShort()
          val serializedExpandedContextDescription = ByteArray(size)
          src.readFully(serializedExpandedContextDescription)
          val expanded = deserializeEncryptionContext(
            serializedExpandedContextDescription.toString(Charsets.UTF_8)
          )
          context.putAll(expanded!!)
        }
        EntryType.ENCRYPTION_CONTEXT.type -> {
          val size = src.readUnsignedShort()
          val serializedContext = ByteArray(size)
          src.readFully(serializedContext)
          context = deserializeEncryptionContext(
            serializedContext.toString(Charsets.UTF_8)
          )!!.toMutableMap()
        }
        EntryType.CIPHERTEXT.type -> {
          ciphertext = readCiphertext(src)
        }
      }
      if (ciphertext == null && src.read() == EntryType.CIPHERTEXT.type) {
        ciphertext = readCiphertext(src)
      }
      if (ciphertext == null) {
        throw InvalidCiphertextFormatException("no ciphertext found")
      }

      return Pair(ciphertext, context)
    }

    private fun deserializeEncryptionContext(serialized: String): Map<String, String?>? {
      if (serialized.isEmpty()) {
        return mapOf()
      }

      return serialized.split("|")
        .map { pair ->
          val components = pair.split("=")
          components.first() to components.getOrNull(1)
        }
        .toMap()
    }
  }

  private enum class EntryType(val type: Int) {
    UNDEFINED(0),
    EXPANDED_CONTEXT_DESCRIPTION(1),
    ENCRYPTION_CONTEXT(2),
    SIZED_CIPHERTEXT(3),
    CIPHERTEXT(4)
  }

  /**
   * Some common context keys are typically taken from the environment
   * and can be compactly encoded via a bitmask; keys and their bit offsets are defined below.
   *
   * Maximum value types supported is 15.
   */
  private enum class ContextKey constructor(val index: Int) {
    UNDEFINED(1 shl 0),
    TABLE_NAME(1 shl 1),
    DATABASE_NAME(1 shl 2),
    COLUMN_NAME(1 shl 3),
    SHARD_NAME(1 shl 4),
    PRIMARY_ID(1 shl 5),
    EVENT_TOPIC(1 shl 6),
    SERVICE_NAME(1 shl 7),
    CUSTOMER_TOKEN(1 shl 8),
  }

  class InvalidCiphertextFormatException(message: String) : GeneralSecurityException(message)
  class EncryptionContextMismatchException(message: String) : GeneralSecurityException(message)
  class MissingEncryptionContextException :
    GeneralSecurityException("expected a non empty map of strings")

  class UnexpectedEncryptionContextException :
    GeneralSecurityException("expected null as encryption context")

  class InvalidEncryptionContextException(message: String) : GeneralSecurityException(message)
}
