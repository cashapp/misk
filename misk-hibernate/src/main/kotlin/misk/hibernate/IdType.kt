package misk.hibernate

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.ResultSetIdentifierConsumer
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/** Binds Id<*> in the DB to a bigint in MySQL. */
class IdType : UserType, ResultSetIdentifierConsumer {

  override fun hashCode(x: Any?) = (x as Id<*>).hashCode()

  override fun deepCopy(value: Any?) = value

  override fun replace(original: Any, target: Any, owner: Any?) = original

  override fun equals(x: Any?, y: Any?): Boolean = (x as? Id<*>) == (y as? Id<*>)

  override fun returnedClass() = Id::class.java

  override fun assemble(cached: Serializable, owner: Any?): Any = Id<DbPlaceholder>(cached as Long)

  override fun disassemble(value: Any?) = (value as Id<*>).id

  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st.setNull(index, Types.BIGINT)
    } else {
      st.setLong(index, (value as Id<*>).id)
    }
  }

  override fun nullSafeGet(
    rs: ResultSet,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val result = rs.getLong(names[0])
    return if (result != 0L) Id<DbPlaceholder>(result) else null
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.BIGINT)

  override fun consumeIdentifier(resultSet: ResultSet): Serializable = Id<DbPlaceholder>(
    resultSet.getLong(1)
  )

  /** This placeholder exists so we can create an Id<*>() without a type parameter. */
  private class DbPlaceholder : DbEntity<DbPlaceholder> {
    override val id: Id<DbPlaceholder> get() = throw IllegalStateException("unreachable")
  }
}
