package misk.hibernate

import com.google.crypto.tink.Aead
import misk.crypto.AeadKeyManager
import misk.crypto.encrypt
import okio.ByteString
import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.type.spi.TypeConfigurationAware

internal class SecretColumnType : UserType, ParameterizedType, TypeConfigurationAware {
  companion object {
    const val FIELD_ENCRYPTION_KEY_NAME: String = "key_name"
  }
  private lateinit var aead: Aead
  private lateinit var _typeConfiguration: TypeConfiguration

  override fun setTypeConfiguration(typeConfiguration: TypeConfiguration) {
    _typeConfiguration = typeConfiguration
  }

  override fun getTypeConfiguration(): TypeConfiguration = _typeConfiguration

  override fun setParameterValues(parameters: Properties) {
    val keyManager = _typeConfiguration.metadataBuildingContext.bootstrapContext.serviceRegistry.injector
        .getInstance(AeadKeyManager::class.java)
    val keyName = parameters.getProperty(FIELD_ENCRYPTION_KEY_NAME)
    aead = keyManager[keyName] ?:
        throw HibernateException("Cannot set field, key $keyName not found")
  }

  override fun hashCode(x: Any?): Int = x.hashCode()

  override fun deepCopy(value: Any?) = value

  override fun replace(original: Any?, target: Any?, owner: Any?) = original

  override fun equals(x: Any?, y: Any?) = x == y

  override fun returnedClass() = ByteArray::class.java

  override fun assemble(cached: Serializable?, owner: Any?) = cached

  override fun disassemble(value: Any?) = value as Serializable

  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st.setNull(index, Types.BINARY)
    } else {
      val encrypted = aead.encrypt(value as ByteArray, null)
      st.setBytes(index, encrypted)
    }
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val result = rs?.getBytes(names[0])
    return result?.let { aead.decrypt(it, null) }
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.BINARY)
}