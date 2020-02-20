package misk.hibernate.encryption

import com.google.common.primitives.Chars
import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import com.google.crypto.tink.DeterministicAead
import java.io.Serializable
import javax.inject.Inject
import kotlin.reflect.KClass
import misk.crypto.DeterministicAeadKeyManager
import misk.crypto.FieldLevelEncryptionPacket
import misk.hibernate.Transformer
import misk.hibernate.TransformerContext
import org.hibernate.HibernateException

class EncryptFieldTransformer(context: TransformerContext) : Transformer(context) {

  private val type = context.arguments["type"] as EncryptedFieldType

  private val indexableContext =
          if (type == EncryptedFieldType.Indexable)
            mutableMapOf(
                    FieldLevelEncryptionPacket.ContextKey.TABLE_NAME.name to context.tableName,
                    FieldLevelEncryptionPacket.ContextKey.COLUMN_NAME.name to context.columnName)
          else mutableMapOf()

  private val daead: DeterministicAead by lazy {
    val keyName = context.arguments["keyName"] as String?
            ?: throw HibernateException("keyName is missing from EncryptedField")

    daeadKeyManager[keyName]
  }

  @Inject
  lateinit var daeadKeyManager: DeterministicAeadKeyManager

  override fun assemble(owner: Any?, value: Serializable): Any {
    if (type == EncryptedFieldType.NonDeterministic) {
      return deserialize(value as ByteArray, context.field)
    }

    val packet = FieldLevelEncryptionPacket.fromByteArray(value as ByteArray)
    val aad = packet.getAeadAssociatedData(indexableContext)
    return daead.decryptDeterministically(packet.payload, aad)
  }

  override fun disassemble(value: Any): Serializable {
    if (type == EncryptedFieldType.NonDeterministic) {
      return serialize(value)
    }

    val serialized = serialize(value)
    val packet = FieldLevelEncryptionPacket.Builder()
            .also { builder ->
              indexableContext.keys.forEach { key -> builder.addContextEntryWithValueFromEnv(key) }
            }
            .build()

    val aad = packet.getAeadAssociatedData(indexableContext)

    val encrypted = daead.encryptDeterministically(serialized, aad)

    return packet.serializeForStorage(encrypted, indexableContext)
  }

  companion object {

    fun serialize(value: Any): ByteArray = when (value) {
      is ByteArray -> value
      is String -> value.toByteArray(Charsets.UTF_8)
      is Double -> Longs.toByteArray(value.toBits())
      is Int -> Ints.toByteArray(value)
      is Char -> Chars.toByteArray(value)
      else -> throw HibernateException("Unsupported field type")
    }

    fun deserialize(buffer: ByteArray, type: KClass<*>): Any = when (type) {
      ByteArray::class -> buffer
      String::class -> buffer.toString(Charsets.UTF_8)
      Double::class -> Double.fromBits(Longs.fromByteArray(buffer))
      Int::class -> Ints.fromByteArray(buffer)
      Char::class -> Chars.fromByteArray(buffer)
      else -> throw HibernateException("Unsupported field type!")
    }
  }
}
