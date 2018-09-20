package misk.hibernate

import io.opentracing.mock.MockTracer
import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.hibernate.annotation.keyspace
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
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var tracer: MockTracer

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

    // TODO waiting for this PR: https://github.com/vitessio/vitess/pull/4199
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
}

private fun <T : DbEntity<T>> Transacter.save(entity: T): Id<T> = transaction { it.save(entity) }

class WrongShardException : RuntimeException()

inline fun <reified T : DbRoot<T>> Transacter.createInSeparateShard(
  id: Id<T>,
  crossinline factory: () -> T
): Id<T> = retry(10, FlatBackoff()) {
  transaction { session ->
    val newId = session.save(factory())
    if (newId.shard(session) == id.shard(session)) {
      throw WrongShardException()
    }
    newId
  }
}

inline fun <reified T : DbRoot<T>> Id<T>.shard(session: Session): Shard {
  val keyspace = T::class.java.getAnnotation(misk.hibernate.annotation.Keyspace::class.java)
  val shards = session.shards(keyspace.keyspace())
  return shards.find { it.contains(this.shardKey()) }!!
}
