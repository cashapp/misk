package misk.hibernate

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType

internal class ProtoColumnType<T : Message<T, *>> : UserType, ParameterizedType {
  private lateinit var protoAdapter: ProtoAdapter<T>

  @Suppress("UNCHECKED_CAST")
  override fun setParameterValues(properties: Properties) {
    val clazz = properties.getField("protoColumnField")!!.type
    protoAdapter = ProtoAdapter.get(clazz) as ProtoAdapter<T>
  }

  override fun hashCode(x: Any) = x.hashCode()

  override fun deepCopy(value: Any?) = value

  override fun replace(original: Any?, target: Any, owner: Any?) = original

  override fun equals(x: Any?, y: Any?) = x == y

  override fun returnedClass() = this.javaClass

  override fun assemble(cached: Serializable, owner: Any?) = cached

  @Suppress("UNCHECKED_CAST") // Hibernate promises to call us only with the types we support.
  override fun disassemble(value: Any?) = value as Serializable

  @Suppress("UNCHECKED_CAST")
  override fun nullSafeSet(st: PreparedStatement, value: Any?, index: Int, session: SharedSessionContractImplementor?) {
    if (value == null) {
      st.setNull(index, Types.BLOB)
    } else {
      st.setBytes(index, protoAdapter.encode(value as T))
    }
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?,
  ): Any? {
    val result = rs?.getBytes(names[0])
    return if (result != null) protoAdapter.decode(result) else null
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARCHAR)
}
