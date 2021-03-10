package misk.hibernate

import com.google.common.annotations.VisibleForTesting
import misk.jdbc.DataSourceConfig
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.tabletDoesNotExists
import javax.persistence.PersistenceException
import kotlin.reflect.KClass

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

  /**
   * Runs a non-transactional session against a read replica.
   *
   * A few things that are different with replica reads:
   * * Replica reads are (obviously?) read only.
   * * Consistency is eventual. If your application thread just wrote something in a transaction
   *   you may not see that write with a replica read as the write may not have replicated
   *   to the replica yet.
   * * There may be time jumping. As each query may end up at a separate replica that will likely
   *   be at a separate point in the replica stream. That means each query can jump back or forward
   *   in "time". (There is some support for internally consistent replica reads that peg a single
   *   replica in Vitess but we're not using that. If you need that functionality reach out to
   *   #vitess)
   * * Full scatters are allowed since you can increase the availability of these by adding more
   *   replicas.
   * * If no reader is configured for replica reads when installing the [HibernateModule], this
   *   method will throw an [IllegalStateException].
   * * Note: You can do it another way, where you annotate the [Transacter] with the readerQualifer
   *   defined by [HibernateModule], which will use the read only replica as the datasource.
   *
   */
  fun <T> replicaRead(block: (session: Session) -> T): T

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

  fun config(): DataSourceConfig

  /** Returns KClasses for the bound DbEntities for the transacter */
  fun entities(): Set<KClass<out DbEntity<*>>>
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
 * Runs a read on master first then tries it on replicas on failure. This method is here only for
 * health check purpose for standby regions.
 */
fun <T> Transacter.failSafeRead(block: (session: Session) -> T): T =
  try {
    transaction {
      block(it)
    }
  } catch (e: PersistenceException) {
    if (tabletDoesNotExists(e)) {
      replicaRead {
        block(it)
      }
    } else {
      throw e
    }
  }

fun <T> Transacter.failSafeRead(shard: Shard, block: (session: Session) -> T): T =
  failSafeRead { it.target(shard) { block(it) } }

/**
 * Thrown to explicitly trigger a retry, subject to retry limits and config such as noRetries().
 */
class RetryTransactionException(
  message: String? = null,
  cause: Throwable? = null
) : Exception(message, cause)
