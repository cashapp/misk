package misk.crypto

import com.google.common.io.ByteStreams
import misk.crypto.FieldLevelEncryptionPacket.EntryType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutput
import org.apache.commons.collections4.map.CaseInsensitiveMap

/**
 * [FieldLevelEncryptionPacket] defines a packet format that encodes a ciphertext and associated metadata, typically
 * additionally authenticated data (AAD). Its designed to be extendable and compact.  Additionally authenticated data is
 * a context consisting of key: value pairs. Keys are canonicalized to be lower-case.
 *
 * The format is a variation on the basic Type-Length-Value encoding scheme.
 *
 * The packet is defined as
 *
 * [2 byte context bitmask][ ... 1 or more TLV packets ... ]
 *
 * Where almost every TLV packet is:
 *
 *   [1 byte type][2 byte length][N byte value]
 *
 * with the exception of type [EntryType.PAYLOAD_REMAINDER], in which case the size is skipped and payload is considered
 * to be until the end of the packet.
 *
 */
class FieldLevelEncryptionPacket private constructor(
  internal val context: Map<String, String?>,
  var payload: ByteArray? = null
) {
  /**
   * Some common context keys are typically taken from the environment and can be compactly encoded via a bitmask; keys
   * and their bit offsets are defined below.
   *
   * Maximum value types supported is 15.
   */
  enum class ContextKey constructor(val index: Int) {
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

  /**
   * Types of possible TLV packets
   */
  enum class EntryType(val type: Int) {
    UNDEFINED(0),

    /**
     * A description of a context that can have missing values (but all keys that are included). Also leaves out fields
     * marked in the bitmask.
     */
    EXPANDED_CONTEXT_DESCRIPTION(1),

    /**
     * The complete serialized AAD, what is required for AEAD.
     */
    COMPLETE_AAD(2),

    /**
     * A payload that has a size field; used for storing packets that we'd like to append to.
     */
    PAYLOAD_SIZED(3),

    /**
     * A marker for a payload that just goes until end of packet.
     */
    PAYLOAD_REMAINDER(4)
  }

  /**
   * Possible inner fields a packet can have.
   *
   * TODO(yivnitskiy): Move 'parsing' logic from the 'when' below to each type
   */
  sealed class Field(open val payload: ByteArray) {
    data class ExpandedContextDescription(override val payload: ByteArray) : Field(payload) {
      companion object {
        val type: EntryType = EntryType.EXPANDED_CONTEXT_DESCRIPTION
      }

      override fun type() = type
    }

    data class CompleteAad(override val payload: ByteArray) : Field(payload) {
      companion object {
        val type: EntryType = EntryType.COMPLETE_AAD
      }

      override fun type() = type
    }

    data class PayloadSized(override val payload: ByteArray) : Field(payload) {
      companion object {
        val type: EntryType = EntryType.PAYLOAD_SIZED
      }

      override fun type() = type
    }

    data class PayloadRemainder(override val payload: ByteArray) : Field(payload) {
      companion object {
        val type: EntryType = EntryType.PAYLOAD_REMAINDER
      }

      override fun type() = type

      override fun writeToStreamIfNotEmpty(s: DataOutput) {
        if (payload.isEmpty())
          return

        s.writeByte(this.type().type)
        s.write(payload)
      }
    }

    abstract fun type(): EntryType

    open fun writeToStreamIfNotEmpty(s: DataOutput) {
      if (payload.isEmpty())
        return

      if (payload.size > Short.MAX_VALUE) {
        throw InvalidEncryptionContextException("Field is too large (${payload.size} is greater than ${Short.MAX_VALUE})")
      }

      s.writeByte(this.type().type)
      s.writeShort(payload.size)
      s.write(payload)
    }

    companion object {
      fun readField(s: DataInputStream): Field? {
        val type = s.read()
        if (type < 0)
          return null
        return when (type) {
          ExpandedContextDescription.type.type -> {
            val size = s.readUnsignedShort()
            val payload = ByteArray(size)
            s.readFully(payload, 0, size)
            ExpandedContextDescription(payload)
          }

          CompleteAad.type.type -> {
            val size = s.readUnsignedShort()
            val payload = ByteArray(size)
            s.readFully(payload, 0, size)
            CompleteAad(payload)
          }

          PayloadSized.type.type -> {
            val size = s.readUnsignedShort()
            val payload = ByteArray(size)
            s.readFully(payload, 0, size)
            PayloadSized(payload)
          }

          PayloadRemainder.type.type -> {
            val stream = ByteArrayOutputStream()
            var byteRead: Int
            while (true) {
              byteRead = s.read()
              if (byteRead < 0)
                break
              stream.write(byteRead)
            }
            PayloadRemainder(stream.toByteArray())
          }
          else -> throw InvalidEncryptionContextException("Bad packet format, unknown field type: $type")
        }
      }
    }
  }

  class Builder {
    private var context = CaseInsensitiveMap<String, String>()

    /**
     * Add an entry to the context that can have an explicit value associated with it.
     */
    fun addContextEntry(key: String, value: String) = also {
      if (key in context) throw InvalidEncryptionContextException("trying to add a context value that already exists ($key)")
      if (key.toUpperCase() == ContextKey.UNDEFINED.name) throw InvalidEncryptionContextException("undefined is a reserved entry")

      context[key] = value
    }

    fun addContextEntry(entry: Pair<String, String>) = this.addContextEntry(entry.first, entry.second)

    fun addContextEntryWithValueFromEnv(key: String) = also {
      if (key in context) throw InvalidEncryptionContextException("trying to add a context value that already exists ($key)")

      context[key] = null
    }

    fun build() = FieldLevelEncryptionPacket(context)
  }

  companion object {

    const val CurrentVersion = 1

    fun fromByteArray(srcArray: ByteArray/*, env: Map<String, String>*/): FieldLevelEncryptionPacket {
      val src = DataInputStream(ByteArrayInputStream(srcArray))

      val version = src.readUnsignedByte()
      val bitmask = src.readUnsignedShort()
      var context = mutableMapOf<String, String?>()
      var payload: ByteArray? = null

      if (version != CurrentVersion) {
        throw InvalidEncryptionContextException("unsupported packet version")
      }

      try {
        context.putAll(bitmask.getSetBits().map { bit ->
          val contextKey = ContextKey.values().single { it.ordinal == bit }
          contextKey.name.toLowerCase() to null
        })
      } catch (e: Throwable) {
        throw InvalidEncryptionContextException("unsupported flag set in context bitmask", e)
      }

      while (true) {
        val field = Field.readField(src) ?: break
        when (field) {
          is Field.ExpandedContextDescription -> {
            val expanded = deserializeContext(field.payload.toString(Charsets.UTF_8))
            context.putAll(expanded)
          }
          is Field.CompleteAad -> {
            context = deserializeContext(field.payload.toString(Charsets.UTF_8)).toMutableMap()
          }
          is Field.PayloadSized -> {
            require(payload == null) { "can only have a single payload" }
            payload = field.payload
          }
          is Field.PayloadRemainder -> {
            require(payload == null) { "can only have a single payload" }
            payload = field.payload
          }
        }
      }
      return FieldLevelEncryptionPacket(context, payload)
    }

    private fun deserializeContext(ctxStr: String) = ctxStr.split("|").map { tup ->
      val components = tup.split("=")
      when (components.size) {
        1, 2 -> components.first() to components.getOrNull(1)
        else -> throw InvalidEncryptionContextException("Incorrectly formatted context")
      }
    }.toMap()
  }

  /**
   * Merge the packet context with the environment context and serialize to a byte array.
   *
   */
  fun getAeadAssociatedData(envContext: Map<String, String>): ByteArray {
    val env = CaseInsensitiveMap(envContext)
    val completeContext = context.mapValues { (k, v) ->
      if (v == null) {
        env.getOrElse(k) { throw InvalidEncryptionContextException("Missing value ($k) in env") }
      } else {
        if (env.contains(k) && env[k] != v) {
          throw InvalidEncryptionContextException("env and context disagree about values ('${env[k]}' != '$v')")
        }
        v
      }
    }

    return serializeContext(completeContext)
  }

  /**
   * Serialize the packet; externally called
   *
   */
  fun serializeForStorage(
    payload: ByteArray,
    env: Map<String, String>,
    includeCompleteAad: Boolean = FieldLevelEncryptionModule.IncludeCompleteAadInPacket
  ): ByteArray {
    val (bitmask, encodedContext) = encodeContextWithBitmask()

    val bb = ByteStreams.newDataOutput()
    bb.writeByte(CurrentVersion)
    bb.writeShort(bitmask)

    if (includeCompleteAad) {
      Field.CompleteAad(getAeadAssociatedData(env)).writeToStreamIfNotEmpty(bb)
    } else {
      Field.ExpandedContextDescription(encodedContext).writeToStreamIfNotEmpty(bb)
    }

    // PAYLOAD_SIZED is not used yet
    Field.PayloadRemainder(payload).writeToStreamIfNotEmpty(bb)

    return bb.toByteArray()
  }

  /**
   * Encode the current context into the bitmask and long form for remaining values.
   *
   *  [full]: Whether to expand the entire context beside the bitmask.
   *
   */
  internal fun encodeContextWithBitmask(full: Boolean = false): Pair<Int, ByteArray> {
    val keysForBitfield = ContextKey.values().map { it.name }

    val bitfield = context.keys
            .filter { k -> keysForBitfield.contains(k.toUpperCase()) }
            .map { k -> ContextKey.valueOf(k.toUpperCase()).index }
            .fold(0) { acc, bit -> acc or bit }

    val expanded = if (full) {
      context
    } else {
      context.filter { (k, _) -> !keysForBitfield.contains(k.toUpperCase()) }
    }

    return Pair(bitfield, serializeContext(expanded))
  }

  /**
   * Simply serializes the context to its byte representation while performing some error checking.
   */
  private fun serializeContext(context: Map<String, String?>): ByteArray {

    context.mapNotNull { (key, v) ->
      if ((key + v.orEmpty()).indexOfAny(charArrayOf('=', '|')) >= 0) key else null
    }.ifNotEmpty {
      val where = if (size > 1) "keys" else "key"
      throw InvalidEncryptionContextException("Bad characters ('=', '|') in context $where: ${joinToString(", ")}")
    }

    return context.asSequence()
            .sortedBy { (k, v) -> k.toLowerCase() + (v?.toLowerCase() ?: "") }
            .map { (key, value) -> if (value == null) key else "$key=$value" }
            .joinToString("|")
            .toByteArray()
  }
}

fun Int.getSetBits() = (0..Int.SIZE_BITS).filter { (1 shl it) and this != 0 }

inline fun <T, C : Collection<T>, O> C.ifNotEmpty(body: C.() -> O?): O? = if (isNotEmpty()) this.body() else null
