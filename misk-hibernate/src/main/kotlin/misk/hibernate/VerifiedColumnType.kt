package misk.hibernate

import com.google.crypto.tink.Mac
import misk.crypto.MacKeyManager
import misk.logging.getLogger
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
import java.util.Base64
import java.util.Objects
import java.util.Properties

internal class VerifiedColumnType : UserType, ParameterizedType, TypeConfigurationAware {

  companion object {
    const val FIELD_HMAC_KEY_NAME: String = "keyName"
    val logger = getLogger<VerifiedColumnType>()
  }
  private lateinit var mac: Mac
  private lateinit var keyName: String
  private lateinit var _typeConfiguration: TypeConfiguration
  override fun getTypeConfiguration(): TypeConfiguration = _typeConfiguration

  override fun setTypeConfiguration(typeConfiguration: TypeConfiguration) {
    _typeConfiguration = typeConfiguration
  }

  override fun setParameterValues(parameters: Properties) {
    keyName = parameters.getProperty(FIELD_HMAC_KEY_NAME)
    val keyManager = _typeConfiguration.metadataBuildingContext.bootstrapContext.serviceRegistry.injector
        .getInstance(MacKeyManager::class.java)
    mac = keyManager[keyName]
  }

  override fun hashCode(x: Any?): Int = (x as String?).hashCode()

  override fun deepCopy(value: Any?) = value as String?

  override fun replace(original: Any?, target: Any?, owner: Any?) = deepCopy(original)

  override fun equals(x: Any?, y: Any?) = Objects.equals(x, y)

  override fun returnedClass() = String::class.java

  override fun assemble(cached: Serializable?, owner: Any?) = cached

  override fun disassemble(value: Any?) = value as Serializable?

  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    (value as String?)?.let {
      st.setString(index, it)
      st.setBytes(index + 1, mac.computeMac(it.toByteArray()))
    } ?: st.setNull(index, Types.VARBINARY)
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    return rs?.let {
      val data = rs.getString(names[0])
      val tag = rs.getBytes(names[1])
      try {
        mac.verifyMac(tag, data.toByteArray())
      } catch (e: GeneralSecurityException) {
        val columnCount = rs.metaData.columnCount
        val dataColumnName = 1.rangeTo(columnCount)
            .find { rs.metaData.getColumnLabel(it) == names[0] }
            ?.let { dataColumnIndex -> rs.metaData.getColumnName(dataColumnIndex) }
        val message = "Failed to verify data authenticity: " +
            "${dataColumnName ?: "<unknown_column_name>"}=$data " +
            "MAC=${Base64.getEncoder().encodeToString(tag)}"
        logger.error(e) { message }
        throw HibernateException(message, e)
      }
      return data
    }
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARCHAR, Types.VARBINARY)
}