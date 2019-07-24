package misk.hibernate

import com.google.common.annotations.VisibleForTesting

/**
 * Provides explicit block-based transaction demarcation.
 */
interface Transacter {
  /**
   * Returns true if the calling thread is currently within a transaction block.
   */
  val inTransaction: Boolean

  /**
   * Is the scalability check currently enabled. Use [Session.withoutChecks] to disable checks.
   */
  fun isCheckEnabled(check: Check): Boolean

  /**
   * Starts a transaction on the current thread, executes [block], and commits the transaction.
   * If the block raises an exception the transaction will be rolled back instead of committed.
   *
   * If retries are permitted (the default), a failed but recoverable transaction will be
   * reattempted after rolling back.
   *
   * It is an error to start a transaction if another transaction is already in progress.
   */
  fun <T> transaction(block: (session: Session) -> T): T

  fun retries(maxAttempts: Int = 2): Transacter

  fun noRetries(): Transacter

  /**
   * Creates a new transacter that produces read only sessions. This does not mean the underlying
   * datasource is read only, only that the session produced won't modify the database.
   */
  fun readOnly(): Transacter

  /**
   * Disable cowrite checks for the duration of the session. Useful for quickly setting up test
   * data in testing.
   */
  // TODO(jontirsen): Figure out a way to make this only available for test code
  @VisibleForTesting
  fun allowCowrites(): Transacter
}

fun Transacter.shards() = transaction { it.shards() }

fun Transacter.shards(keyspace: Keyspace) = transaction { it.shards(keyspace) }

/**
 * Commits a transaction with operations of [block].
 *
 * New objects must be persisted with an explicit call to [Session.save].
 * Updates are performed implicitly by modifying objects returned from a query.
 *
 * For example if we were to save a new movie to a movie database, and update the revenue of an
 * existing movie:
 * ```
 * transacter.transaction { session ->
 *   // Saving a new entity to the database needs an explicit call.
 *   val starWars = DbMovie(name = "Star Wars", year = "1977", revenue = 775_400_000)
 *   session.save(starWars)
 *
 *   // Updating a movie from the database is done by modifying the object.
 *   // Changes are saved implicitly.
 *   val movie: DbMovie = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)!!
 *   movie.revenue = 100_000_000
 * }
 *
 */
fun <T> Transacter.transaction(shard: Shard, block: (session: Session) -> T): T =
    transaction { it.target(shard) { block(it) } }

/**
 * Thrown to explicitly trigger a retry, subject to retry limits and config such as noRetries().
 */
class RetryTransactionException(
  message: String? = null,
  cause: Throwable? = null
) : Exception(message, cause)
