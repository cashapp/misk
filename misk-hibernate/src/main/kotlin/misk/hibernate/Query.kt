package misk.hibernate

import kotlin.reflect.KClass

/** Base class for SQL queries. */
interface Query<T> {
  /** How many rows to return. Must be -1 or in range 1..10_000. */
  var maxRows: Int

  fun uniqueResult(session: Session): T?

  fun list(session: Session): List<T>

  /** Creates instances of queries. */
  interface Factory {
    fun <T : Query<*>> newQuery(queryClass: KClass<T>): T
  }
}

inline fun <reified T : Query<*>> Query.Factory.newQuery(): T = newQuery(T::class)

/**
 * Annotates a function on a subinterface of [Query] to indicate which column (or path of columns)
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

/**
 * Annotates a function on a subinterface of [Query] to execute a `SELECT` query. Functions with
 * this annotation must return a `List` to fetch multiple rows results, or a regular type to fetch
 * a unique result.
 */
annotation class Select(
  val path: String = ""
)

/**
 * Annotates a function on a subinterface of [Query] to indicate which columns to order the
 * the selected columns.
 */
annotation class Order(
  val path: String,
  val asc: Boolean = true
)
