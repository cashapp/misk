package misk.hibernate

import kotlin.reflect.KClass

/** Base class for SQL queries. */
interface Query<T> {
  fun uniqueResult(session: Session): T?
  fun <P : Projection> uniqueResultAs(session: Session, projection: KClass<P>): P?

  fun list(session: Session): List<T>
  fun <P : Projection> listAs(session: Session, projection: KClass<P>): List<P>

  /** Creates instances of queries. */
  interface Factory {
    fun <T : Query<*>> newQuery(queryClass: KClass<T>): T
  }
}

inline fun <reified P : Projection> Query<*>.listAs(session: Session) = listAs(session, P::class)

inline fun <reified P : Projection> Query<*>.uniqueResultAs(session: Session) = uniqueResultAs(
    session, P::class)

inline fun <reified T : Query<*>> Query.Factory.newQuery(): T = newQuery(T::class)

/**
 * Annotations a function on a subinterface of [Query] to indicate which column (or path of columns)
 * it constrains and using which operator.
 */
annotation class Constraint(
  val path: String,
  val operator: String = "="
)

/**
 * Annotations a parameter of a data class [Projection] to indicate which column (or path of
 * columns) to populate the parameter with.
 */
annotation class Property(
  val value: String
)
