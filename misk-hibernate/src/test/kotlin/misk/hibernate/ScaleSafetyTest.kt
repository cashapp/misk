package misk.hibernate

import io.opentracing.mock.MockTracer
import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.jdbc.CrossShardQueryException
import misk.jdbc.CrossShardTransactionException
import misk.hibernate.annotation.keyspace
import misk.jdbc.CrossShardQueryDetector
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import javax.inject.Inject

/**
 * Verifies that we're constraining a few things that makes apps hard to scale out.
 */
@MiskTest(startService = true)
class ScaleSafetyTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject @Movies lateinit var crossShardQueryDetector: CrossShardQueryDetector
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun crossShardTransactionsAreDisabled() {
    val jg = transacter.save(
        DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
    val cf = transacter.save(
        DbActor("Carrie Fisher", null))

    val jp = transacter.save(
        DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
    val sw = transacter.createInSeparateShard(jp) {
      DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
    }

    // TODO transaction_mode=SINGLE doesn't work the way I expected it: it doesn't allow cross shard queries either, gotta figure this outCrossShardQueryDetectorTest
//    assertThrows<CrossShardTransactionException> {
      transacter.transaction { session ->
        session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
        session.save(DbCharacter("Leia Organa", session.load(sw), session.load(cf)))
      }
//    }
//    assertThrows<CrossShardTransactionException> {
      transacter.transaction { session ->
        val jpEntity = session.load(jp)
        jpEntity.release_date = LocalDate.now()
        val swEntity = session.load(sw)
        swEntity.release_date = LocalDate.now()
//      }
    }
  }

  @Test
  fun transactionsSpanningEntityGroupsInTheSameShard() {
    val jg = transacter.save(
        DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
    val cf = transacter.save(
        DbActor("Carrie Fisher", null))

    val jp = transacter.save(
        DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
    val sw = transacter.createInSameShard(jp) {
      DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
    }

    // TODO not implemented yet
    // needs a Vitess function to see which keyspace IDs have been touched by a transaction or
    // just a port of the EntityGroupListener from the Square monorepo
//    assertThrows<CrossShardTransactionException> {
    transacter.transaction { session ->
      session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
      session.save(DbCharacter("Leia Organa", session.load(sw), session.load(cf)))
    }
//    }
//    assertThrows<CrossShardTransactionException> {
    transacter.transaction { session ->
      val jpEntity = session.load(jp)
      jpEntity.release_date = LocalDate.now()
      val swEntity = session.load(sw)
      swEntity.release_date = LocalDate.now()
    }
//    }
  }

  @Test
  fun crossShardQueriesAreDetected() {
    assertThrows<CrossShardQueryException> {
      transacter.transaction { session ->
        queryFactory.newQuery<MovieQuery>()
            .releaseDateBefore(LocalDate.of(1977, 6, 15))
            .list(session)
      }
    }
  }

  @Test
  fun crossShardQueriesDetectorCanBeDisabled() {
    transacter.transaction { session ->
      crossShardQueryDetector.disable {
        queryFactory.newQuery<MovieQuery>()
            .releaseDateBefore(LocalDate.of(1977, 6, 15))
            .list(session)
      }
    }
  }
}

private fun <T : DbEntity<T>> Transacter.save(entity: T): Id<T> = transaction { it.save(entity) }

fun Transacter.createInSeparateShard(
  id: Id<DbMovie>,
  factory: () -> DbMovie
): Id<DbMovie> {
  val sw = createUntil(factory) { session, newId ->
    newId.shard(session) != id.shard(session)
  }
  return sw
}

fun Transacter.createInSameShard(
  id: Id<DbMovie>,
  factory: () -> DbMovie
): Id<DbMovie> {
  val sw = createUntil(factory) { session, newId ->
    newId.shard(session) == id.shard(session)
  }
  return sw
}

class NotThereYetException : RuntimeException()

inline fun <reified T : DbRoot<T>> Transacter.createUntil(
  crossinline factory: () -> T,
  crossinline condition: (Session, Id<T>) -> Boolean
): Id<T> = retry(10, FlatBackoff()) {
  transaction { session ->
    val newId = session.save(factory())
    if (!condition(session, newId)) {
      throw NotThereYetException()
    }
    newId
  }
}

inline fun <reified T : DbRoot<T>> Id<T>.shard(session: Session): Shard {
  val keyspace = T::class.java.getAnnotation(misk.hibernate.annotation.Keyspace::class.java)
  val shards = session.shards(keyspace.keyspace())
  return shards.find { it.contains(this.shardKey()) }!!
}
