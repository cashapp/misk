package misk.hibernate

import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.type.spi.TypeConfigurationAware
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

internal class TransformedColumnType : UserType, ParameterizedType, TypeConfigurationAware {
  companion object {
    val TRANSFORMER_CLASS = "transformerClass"
    val TABLE_NAME = "tableName"
    val COLUMN_NAME = "columnName"
    val FIELD = "field"
    val TARGET_TYPE = "targetType"
    val ARGUMENTS = "arguments"
  }

  lateinit var retClass: KClass<*>

  lateinit var transformer: Transformer

  lateinit var typeConfig: TypeConfiguration

  override fun hashCode(x: Any) = x.hashCode()

  override fun deepCopy(value: Any?) = value

  override fun replace(original: Any?, target: Any?, owner: Any?) = original

  override fun equals(x: Any?, y: Any?) = x == y

  override fun returnedClass(): Class<*> = retClass.java

  override fun assemble(cached: Serializable, owner: Any): Any = transformer.assemble(owner, cached)

  override fun disassemble(value: Any): Serializable = transformer.disassemble(value)

  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st.setNull(index, sqlTypes().first())
    } else {
      val disassembled = transformer.disassemble(value)
      st.setByType(retClass, index, disassembled)
    }
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val disassembled = rs?.getByType(retClass, names[0]) ?: return null
    return transformer.assemble(owner, disassembled)
  }

  override fun isMutable() = false

  override fun sqlTypes(): IntArray = intArrayOf(
    when (retClass) {
      ByteArray::class -> Types.VARBINARY
      String::class -> Types.VARCHAR
      Int::class -> Types.INTEGER
      Long::class -> Types.BIGINT
      Byte::class -> Types.SMALLINT
      Boolean::class -> Types.BOOLEAN
      Double::class -> Types.DOUBLE
      Float::class -> Types.FLOAT
      else -> throw HibernateException("Unsupported sql type")
    }
  )

  override fun setParameterValues(parameters: Properties) {
    retClass = parameters[TARGET_TYPE] as KClass<*>

    val field = parameters.getField(FIELD)!!

    @Suppress("UNCHECKED_CAST")
    val ctorArgs = parameters[ARGUMENTS] as? Map<String, *>
      ?: throw HibernateException("Bad Transformer arguments")

    val context = TransformerContext(
      parameters[TABLE_NAME] as String,
      parameters[COLUMN_NAME] as String,
      ctorArgs,
      field.type.kotlin
    )

    @Suppress("UNCHECKED_CAST")
    val transformerClass = parameters[TRANSFORMER_CLASS] as KClass<out Transformer>
    transformer = transformerClass.primaryConstructor?.call(context)
      ?: throw HibernateException("Transformer class missing primary constructor")

    val injector = typeConfig.metadataBuildingContext.bootstrapContext.serviceRegistry.injector
    injector.injectMembers(transformer)
  }

  override fun setTypeConfiguration(typeConfiguration: TypeConfiguration) {
    typeConfig = typeConfiguration
  }

  override fun getTypeConfiguration(): TypeConfiguration = typeConfig
}

fun PreparedStatement.setByType(klass: KClass<*>?, index: Int, value: Any) = when (klass) {
  ByteArray::class -> setBytes(index, value as ByteArray)
  String::class -> setString(index, value as String)
  Int::class -> setInt(index, value as Int)
  Long::class -> setLong(index, value as Long)
  Double::class -> setDouble(index, value as Double)
  Byte::class -> setByte(index, value as Byte)
  Boolean::class -> setBoolean(index, value as Boolean)
  else -> throw HibernateException("unsupported type ${klass?.qualifiedName}")
}

fun ResultSet.getByType(klass: KClass<*>?, columnLabel: String): Serializable? = when (klass) {
  ByteArray::class -> getBytes(columnLabel)
  String::class -> getString(columnLabel)
  Int::class -> getInt(columnLabel)
  Long::class -> getLong(columnLabel)
  Double::class -> getDouble(columnLabel)
  Byte::class -> getByte(columnLabel)
  Boolean::class -> getBoolean(columnLabel)
  else -> throw HibernateException("unsupported type ${klass?.qualifiedName}")
}
