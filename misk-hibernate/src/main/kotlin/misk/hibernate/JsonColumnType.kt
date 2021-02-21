package misk.hibernate

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
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

internal class JsonColumnType<T> : UserType, ParameterizedType, TypeConfigurationAware {
  private lateinit var _typeConfiguration: TypeConfiguration
  private lateinit var jsonAdapter: JsonAdapter<T>

  override fun setTypeConfiguration(typeConfiguration: TypeConfiguration) {
    _typeConfiguration = typeConfiguration
  }

  override fun getTypeConfiguration() = _typeConfiguration

  override fun setParameterValues(properties: Properties) {
    val moshi = typeConfiguration.metadataBuildingContext.bootstrapContext.serviceRegistry.injector
      .getInstance(Moshi::class.java)
    jsonAdapter = moshi.adapter<T>(properties.getField("jsonColumnField")!!.genericType)
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
  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st.setNull(index, Types.CHAR)
    } else {
      st.setString(index, jsonAdapter.toJson(value as T))
    }
  }

  override fun nullSafeGet(
    rs: ResultSet?,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val result = rs?.getString(names[0])
    return if (result != null) jsonAdapter.fromJson(result) else null
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARCHAR)
}
