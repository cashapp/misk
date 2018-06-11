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
  val operator: Operator = Operator.EQ
)

enum class Operator {
  /** `a < b` */
  LT,

  /** `a <= b` */
  LE,

  /** `a = b` */
  EQ,

  /** `a >= b` */
  GE,

  /** `a > b` */
  GT,

  /** `a != b` */
  NE,

  /** `a IN (b1, b2, b3, ...)` */
  IN,

  /** `a IS NOT NULL` */
  IS_NOT_NULL,

  /** `a IS NULL` */
  IS_NULL
}
