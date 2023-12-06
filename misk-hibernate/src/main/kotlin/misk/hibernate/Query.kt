package misk.hibernate

import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.JoinType
import javax.persistence.criteria.JoinType.LEFT
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import javax.persistence.criteria.Selection
import kotlin.reflect.KClass

/** Base class for SQL queries. */
interface Query<T> {
  /** Set the first row to retrieve. The default is 0. */
  var firstResult: Int

  /** How many rows to return. Must be -1 or in range 1..10_000. */
  var maxRows: Int

  /** Constrain a query by operating directly on the JPA criteria builder. */
  fun addJpaConstraint(block: (root: Root<*>, builder: CriteriaBuilder) -> Predicate)

  /** Constrain a query using a path known only at runtime. */
  fun dynamicAddConstraint(path: String, operator: Operator, value: Any? = null)

  fun dynamicAddOrder(path: String, asc: Boolean)

  /** Fetch the given path as a join, using the given joinType */
  fun dynamicAddFetch(path: String, joinType: JoinType)

  /**
   * Adds a SQL hint to the query.
   */
  fun addQueryHint(hint: String)

  fun disableCheck(check: Check)

  /** Asserts that there is either zero or one results. */
  fun uniqueResult(session: Session): T?

  /** Manual projections are returned as a list of cells. Returns null if there were no results. */
  fun dynamicUniqueResult(
    session: Session,
    selection: (CriteriaBuilder, Root<T>) -> Selection<out Any>
  ): List<Any?>?

  fun dynamicUniqueResult(session: Session, projectedPaths: List<String>): List<Any?>?

  fun list(session: Session): List<T>

  /** Manual projections are returned as a list of rows containing a list of cells. */
  fun dynamicList(
    session: Session,
    selection: (CriteriaBuilder, Root<T>) -> Selection<out Any>
  ): List<List<Any?>>

  fun dynamicList(session: Session, projectedPaths: List<String>): List<List<Any?>>

  /** Returns the number of entities deleted. */
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

  fun <Q : Query<*>> clone(): Q

  /** Creates instances of queries. */
  interface Factory {
    fun <Q : Query<*>> newQuery(queryClass: KClass<Q>): Q
    fun <E : DbEntity<E>> dynamicQuery(entityClass: KClass<E>): Query<E>
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
 * Equivalent to Query.addConstraint, but takes the [CriteriaBuilder] as a receiver and returns
 * this. This may be easier to use with method chaining.
 *
 * The root parameter should be used to select which property of the target entity to match against.
 *
 * ```
 * queryFactory.newQuery<OperatorsMovieQuery>()
 *     .constraint { root -> like(root.get("name"), "Jurassic%") }
 *     .count(session)
 * ```
 */
fun <T, Q : Query<T>> Q.constraint(
  block: CriteriaBuilder.(root: Root<*>) -> Predicate
): Q {
  addJpaConstraint { root, criteriaBuilder ->
    criteriaBuilder.block(root)
  }
  return this
}

/**
 * Adds query hint to the query. (Chainable version of [Query.addQueryHint].)
 */
fun <T, Q : Query<T>> Q.queryHint(hint: String): Q {
  addQueryHint(hint)
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

  /** `a = b` if b is not null, `a is null` if b is null */
  EQ_OR_IS_NULL,

  /** `a >= b` */
  GE,

  /** `a > b` */
  GT,

  /** `a != b` */
  NE,

  /** `a IN (b1, b2, b3, ...)` */
  IN,

  /** `a NOT IN (b1, b2, b3, ...)` */
  NOT_IN,

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
 * Annotates a function on a subinterface of [Query] to specify that the association at
 * the given `path` should be fetched in a single query. The type of join used will be
 * specified by `joinType`.
 */
annotation class Fetch(
  val path: String = "",
  val joinType: JoinType = LEFT
)
