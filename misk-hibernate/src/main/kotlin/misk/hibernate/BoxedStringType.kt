package misk.hibernate

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Properties
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Binds any data class that wraps a String to a varchar in MySQL. This is most useful to enforce
 * additional type-safety on identifier columns.
 *
 * To use, create a data class with a single non-null `String` property. The name of this property
 * is not significant.
 *
 * ```
 * data class MovieToken(val token: String)
 * ```
 *
 * To use with comparison operators (ie. WHERE clauses with `<` or `>`) you must also implement
 * `Comparable`:
 *
 * ```
 * data class MovieToken(val token: String) : Comparable<MovieToken> {
 *   override fun compareTo(other: MovieToken) = token.compareTo(other.token)
 * }
 * ```
 */
internal class BoxedStringType<T : Any> : UserType, ParameterizedType {
  private lateinit var boxer: Boxer<T>

  @Suppress("UNCHECKED_CAST")
  override fun setParameterValues(properties: Properties) {
    val javaClass = properties.getField("boxedStringField")!!.type as Class<T>
    this.boxer = boxer(javaClass.kotlin)!!
  }

  override fun hashCode(x: Any) = x.hashCode()

  override fun deepCopy(value: Any?) = value

  override fun replace(original: Any, target: Any, owner: Any?) = original

  override fun equals(x: Any?, y: Any?) = x == y

  override fun returnedClass() = boxer.returnedClass

  override fun assemble(cached: Serializable, owner: Any?) = boxer.box(cached as String)

  @Suppress("UNCHECKED_CAST") // Hibernate promises to call us only with the types we support.
  override fun disassemble(value: Any) = boxer.unbox(value as T)

  override fun nullSafeSet(
    st: PreparedStatement,
    value: Any?,
    index: Int,
    session: SharedSessionContractImplementor?
  ) {
    if (value == null) {
      st.setNull(index, Types.VARCHAR)
    } else {
      st.setString(index, disassemble(value))
    }
  }

  override fun nullSafeGet(
    rs: ResultSet,
    names: Array<out String>,
    session: SharedSessionContractImplementor?,
    owner: Any?
  ): Any? {
    val result = rs.getString(names[0])
    return if (result != null) assemble(result as String, owner) else null
  }

  override fun isMutable() = false

  override fun sqlTypes() = intArrayOf(Types.VARCHAR)

  companion object {
    fun <T : Any> isBoxedString(propertyType: KClass<T>): Boolean {
      return boxer(propertyType) != null
    }

    /**
     * Returns a boxer if `propertyType` is a data class with a single string property. Otherwise
     * this returns null.
     */
    private fun <T : Any> boxer(propertyType: KClass<T>): Boxer<T>? {
      if (!propertyType.isData) return null

      val memberPropertiesList = propertyType.memberProperties.toList()
      if (memberPropertiesList.size != 1) return null

      val onlyProperty: KProperty1<T, *> = memberPropertiesList[0]
      if (onlyProperty.returnType.classifier != String::class) return null
      @Suppress("UNCHECKED_CAST") // Guarded by a runtime check above.
      val stringProperty = onlyProperty as KProperty1<T, String>

      val constructor: KFunction<T>? = propertyType.primaryConstructor
      if (constructor == null) return null // No primary constructor.
      if (constructor.parameters.size != 1) return null // Too many parameters.
      if (constructor.parameters[0].type.classifier != String::class) return null // Wrong type.

      return Boxer(stringProperty, constructor)
    }
  }

  /** Puts a string in a box and takes it out again. */
  data class Boxer<T>(
    val property: KProperty1<T, String>,
    val constructor: KFunction<T>
  ) {
    val returnedClass: Class<*>
      get() = constructor.returnType.javaClass

    fun box(string: String): T = constructor.call(string)

    fun unbox(value: T): String = property.get(value)
  }
}
