package misk.hibernate

import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import okio.ByteString
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType

/** Binds ByteString in the DB to a varbinary in MySQL. */
internal object ByteStringType : UserType {
  override fun hashCode(x: Any) = x.hashCode()

  override fun deepCopy(value: Any?) = value

  override fun replace(original: Any, target: Any, owner: Any?) = original

  override fun equals(x: Any?, y: Any?) = x == y

  override fun returnedClass() = ByteString::class.java

  override fun assemble(cached: Serializable, owner: Any?) = cached as ByteString

  override fun disassemble(value: Any) = value as ByteString

  override fun nullSafeSet(st: PreparedStatement, value: Any?, index: Int, session: SharedSessionContractImplementor?) {
    if (value == null) {
      st.setNull(index, Types.VARBINARY)
    } else {
      st.setBytes(index, (value as ByteString).toByteArray())
    }
  }

  override fun nullSafeGet(
    rs: ResultSet,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?,
  ): Any? {
    val result = rs.getBytes(names[0])
    return if (result != null) ByteString.of(*result) else null
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARBINARY)
}
