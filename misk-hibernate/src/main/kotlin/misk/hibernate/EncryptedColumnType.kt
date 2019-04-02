package misk.hibernate

import misk.crypto.KeyManager
import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties
import javax.inject.Inject
import okio.ByteString.Companion.toByteString

internal class EncryptedColumnType : UserType, ParameterizedType {
  private lateinit var keyName: String
  @Inject lateinit private var keyManager: KeyManager

  override fun setParameterValues(parameters: Properties) {
    keyName = parameters.getProperty("encryptedColumnField")
  }

  override fun hashCode(x: Any?): Int = x.hashCode()

  override fun deepCopy(value: Any?) = value

  override fun replace(original: Any?, target: Any?, owner: Any?) = original

  override fun equals(x: Any?, y: Any?) = x == y

  override fun returnedClass() = this.javaClass

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
      // encrypt the data in set it in the prepared statement
      val cipher = keyManager[keyName] ?:
          throw HibernateException("Cannot set field, $keyName not found")
      val valueBytes = (value as ByteArray).toByteString()
      val encrypted = cipher.encrypt(valueBytes).toByteArray()
      st.setBytes(index, encrypted)
    }
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val cipher = KeyManager()[keyName] ?:
        throw HibernateException("Cannot get encrypted field, $keyName not found")
    val result = rs?.getBytes(names[0])?.toByteString()
    return result?.let { cipher.decrypt(it) }
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.BINARY)

}