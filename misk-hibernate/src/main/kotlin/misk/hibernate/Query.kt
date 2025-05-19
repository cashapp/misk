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
 * Annotates a function on a [Query] interface to indicate which column (or path of columns)
 * it constrains and using which [Operator].
 *
 * You can think of Constraints as the rules used to build the `where` clause of a SQL query.
 *
 * For example, you can query movies by title with a method like this:
 * ```
 * @Constraint(path = "name") // Uses EQ as the default operator.
 * fun matchesTitle(title: String): MovieQuery
 * ```
 * Or query for movies released after a certain date with a method like this:
 * ```
 * @Constraint(path = "release_date", operator = Operator.GT)
 * fun releasedAfter(date: LocalDate): MovieQuery
 * ```
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
 * Annotates a function on a [Query] interface to execute a `SELECT` query. Functions with
 * this annotation must return a `List` to fetch multiple rows results, or a regular type to fetch
 * a unique result.
 *
 * [Select] annotated methods may return single column values, or [Projection]s of multiple columns.
 */
annotation class Select(
  val path: String = "",
  val aggregation: AggregationType = AggregationType.NONE
)

/**
 * Annotates a function on a [Query] interface to indicate by which columns to order the
 * results. Defaults to ascending order.
 */
annotation class Order(
  val path: String,
  val asc: Boolean = true
)

/**
 * Annotates a function on a [Query] interface to specify that the association at
 * the given [path] should be fetched in a single query. The type of join used will be
 * specified by [joinType], and defaults to a LEFT JOIN.
 *
 * If the query will result in a [Projection], and does not need to get the entire entity graph, set
 * [forProjection] to true. This will make the query operate as a regular JOIN query, instead
 * of a JOIN FETCH query.
 */
annotation class Fetch(
  val path: String = "",
  val joinType: JoinType = LEFT,
  val forProjection: Boolean = false,
)

/**
 * Annotates a function on a [Query] interface to indicate that the results should be
 * grouped by the given [paths]. This is most useful with [Projection]s and aggregations.
 */
annotation class Group(
  val paths: Array<String> = []
)

/**
 * Available aggregations which can be applied to a single value [Select] query,
 * or a [Property] of a projection.
 */
enum class AggregationType {
  /** No aggregation is applied. Like `select column`. */
  NONE,
  /** Like `select avg(column)`. */
  AVG,
  /** Like `select count(column)`. */
  COUNT,
  /** Like `select count(distinct column)`. */
  COUNT_DISTINCT,
  /** Like `select max(column)`. */
  MAX,
  /** Like `select min(column)`. */
  MIN,
  /** Like `select sum(column)`. */
  SUM,
}
