package misk.hibernate

import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import misk.crypto.AeadKeyManager
import misk.crypto.DeterministicAeadKeyManager
import misk.crypto.KeyNotFoundException
import misk.logging.getLogger
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
import java.util.Objects
import java.util.Properties

internal class SecretColumnType : UserType, ParameterizedType, TypeConfigurationAware {
  companion object {
    const val FIELD_ENCRYPTION_KEY_NAME: String = "key_name"
    const val FIELD_ENCRYPTION_INDEXABLE: String = "indexable"
    val logger = getLogger<SecretColumnType>()
  }

  private lateinit var encryptionAdapter: EncryptionAdapter
  private lateinit var keyName: String
  private lateinit var _typeConfiguration: TypeConfiguration

  override fun setTypeConfiguration(typeConfiguration: TypeConfiguration) {
    _typeConfiguration = typeConfiguration
  }

  override fun getTypeConfiguration(): TypeConfiguration = _typeConfiguration

  override fun setParameterValues(parameters: Properties) {
    keyName = parameters.getProperty(FIELD_ENCRYPTION_KEY_NAME)
    val indexable = parameters.getProperty(FIELD_ENCRYPTION_INDEXABLE)!!.toBoolean()

    encryptionAdapter = if (indexable) {
      DeterministicAeadAdapter(_typeConfiguration, keyName)
    } else {
      AeadAdapter(_typeConfiguration, keyName)
    }
  }

  override fun hashCode(x: Any): Int = (x as ByteArray).hashCode()

  override fun deepCopy(value: Any?) = (value as ByteArray?)?.copyOf()

  override fun replace(original: Any?, target: Any?, owner: Any?) = (original as ByteArray).copyOf()

  override fun equals(x: Any?, y: Any?): Boolean = Objects.equals(x, y)

  override fun returnedClass() = ByteArray::class.java

  override fun assemble(cached: Serializable?, owner: Any?): ByteArray {
    return encryptionAdapter.decrypt(cached as ByteArray, null)
  }

  /**
   * This method is used by Hibernate when caching values, see [org.hibernate.type.Type.disassemble].
   * This implementation makes sure that data is stored encrypted even when being cached in memory.
   */
  override fun disassemble(value: Any?): Serializable {
    return encryptionAdapter.encrypt(value as ByteArray, null)
  }

  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st.setNull(index, Types.VARBINARY)
    } else {
      val encrypted = encryptionAdapter.encrypt(value as ByteArray, null)
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
    return result?.let {
      try {
        encryptionAdapter.decrypt(it, null)
      } catch (e: java.security.GeneralSecurityException) {
        throw HibernateException(e)
      }
    }
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARBINARY)
}

internal interface EncryptionAdapter {
  fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray
  fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray
}

internal class AeadAdapter(typeConfig: TypeConfiguration, keyName: String) : EncryptionAdapter {
  private val keyManager =
    typeConfig.metadataBuildingContext.bootstrapContext.serviceRegistry.injector
      .getInstance(AeadKeyManager::class.java)

  val aead: Aead by lazy {
    try {
      keyManager[keyName]
    } catch (ex: KeyNotFoundException) {
      throw HibernateException("Cannot set field, key $keyName not found")
    }
  }

  override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
    return aead.encrypt(plaintext, associatedData)
  }

  override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
    return aead.decrypt(ciphertext, associatedData)
  }
}

internal class DeterministicAeadAdapter(typeConfig: TypeConfiguration, keyName: String) :
  EncryptionAdapter {

  private val keyManager =
    typeConfig.metadataBuildingContext.bootstrapContext.serviceRegistry.injector
      .getInstance(DeterministicAeadKeyManager::class.java)
  val daead: DeterministicAead by lazy {
    try {
      keyManager[keyName]
    } catch (ex: KeyNotFoundException) {
      throw HibernateException("Cannot set field, key $keyName not found")
    }
  }

  override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray {
    // DeterministicAEAD throws if associatedData is null, so we pass an empty array if it is.
    return daead.encryptDeterministically(plaintext, associatedData ?: byteArrayOf())
  }

  override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray {
    return daead.decryptDeterministically(ciphertext, associatedData ?: byteArrayOf())
  }
}
