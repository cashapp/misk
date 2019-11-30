package misk.hibernate

import kotlin.reflect.KClass

/** Base class for SQL queries. */
interface Query<T> {
  /** How many rows to return. Must be -1 or in range 1..10_000. */
  var maxRows: Int

  fun disableCheck(check: Check)

  fun uniqueResult(session: Session): T?

  fun list(session: Session): List<T>

  /**
   * the number of entities deleted
   */
  fun delete(session: Session): Int

  /**
   * Returns the number of rows that match the query.
   *
   * **Warning:** The performance of this operation is comparable to a SELECT. MySQL scans all of
   * the counted rows. A query that returns a count of 5000 is typically 10 times slower than a
   * query that returns a count of 500.
   */
  fun count(session: Session): Long

  fun <T : Query<*>> newOrBuilder(): OrBuilder<T>

  /** Creates instances of queries. */
  interface Factory {
    fun <Q : Query<*>> newQuery(queryClass: KClass<Q>): Q
  }
}

inline fun <reified T : Query<*>> Query.Factory.newQuery(): T = newQuery(T::class)

/**
 * This functional interface accepts a set of options. Each option lambda is executed within the
 * scope of a query. It is inappropriate to call methods like list() and uniqueResult() on this
 * query.
 */
interface OrBuilder<Q : Query<*>> {
  fun option(lambda: Q.() -> Unit)
}

/**
 * Collects options that are all OR'd together. If any are true the predicate matches.
 *
 * ```
 * queryFactory.newQuery<OperatorsMovieQuery>()
 *     .or {
 *       option { name("Rocky 1") }
 *       option { name("Rocky 3") }
 *     }
 *     .list()
 * ```
 *
 * Each option has a list of constraints that are themselves AND'd together.
 */
inline fun <T, reified Q : Query<T>> Q.or(lambda: OrBuilder<Q>.() -> Unit): Q {
  newOrBuilder<Q>().lambda()
  return this
}

inline fun <T, reified Q : Query<T>> Q.allowTableScan(): Q {
  this.disableCheck(Check.TABLE_SCAN)
  return this
}

inline fun <T, reified Q : Query<T>> Q.allowFullScatter(): Q {
  this.disableCheck(Check.FULL_SCATTER)
  return this
}

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

/**
 * Annotates a function on a subinterface of [Query] to indicate a relationship to fetch eagerly.
 * Currently the only strategy to do this uses a LEFT JOIN, so the relationship will be fetched in
 * a single query.
 */
annotation class FetchEagerly(
  val property: String,
  val strategy: EagerFetchStrategy = EagerFetchStrategy.LEFT_JOIN
)

enum class EagerFetchStrategy {
  LEFT_JOIN,
}
