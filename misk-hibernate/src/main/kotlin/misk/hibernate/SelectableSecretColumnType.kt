package misk.hibernate

import com.google.crypto.tink.DeterministicAead
import misk.crypto.DeterministicAeadKeyManager
import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.type.spi.TypeConfigurationAware
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.security.GeneralSecurityException
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Objects
import java.util.Properties

class SelectableSecretColumnType : UserType, ParameterizedType, TypeConfigurationAware {
  companion object {
    const val FIELD_ENCRYPTION_KEY_NAME: String = "key_name"
  }
  private lateinit var keyName: String
  private lateinit var daead: DeterministicAead
  private lateinit var typeConfig: TypeConfiguration

  override fun hashCode(x: Any?) = (x as ByteArray).hashCode()

  override fun deepCopy(value: Any?) = (value as ByteArray?)?.copyOf()

  override fun replace(original: Any?, target: Any?, owner: Any?) = (original as ByteArray).copyOf()

  override fun equals(x: Any?, y: Any?) = Objects.equals(x, y)

  override fun returnedClass() = ByteArray::class.java

  override fun assemble(cached: Serializable?, owner: Any?) =
      daead.decryptDeterministically(cached as ByteArray, byteArrayOf())

  override fun disassemble(value: Any?) =
      daead.encryptDeterministically(value as ByteArray, byteArrayOf())

  override fun nullSafeSet(
    st: PreparedStatement?,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st?.setNull(index, Types.VARBINARY)
    } else {
      value as ByteArray
      val encrypted = daead.encryptDeterministically(value, byteArrayOf())
      st?.setBytes(index, encrypted)
    }
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val result = rs?.getBytes(names[0])
    return result?.let {
      try {
        daead.decryptDeterministically(result, byteArrayOf())
      } catch (e: GeneralSecurityException) {
        throw HibernateException(e)
      }
    }
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARBINARY)

  override fun setParameterValues(parameters: Properties) {
    keyName = parameters.getProperty(FIELD_ENCRYPTION_KEY_NAME)
    val keyManager = typeConfig.metadataBuildingContext.bootstrapContext
        .serviceRegistry.injector.getInstance(DeterministicAeadKeyManager::class.java)
    daead = keyManager[keyName]
  }

  override fun setTypeConfiguration(typeConfiguration: TypeConfiguration) {
    typeConfig = typeConfiguration
  }

  override fun getTypeConfiguration() = typeConfig
}