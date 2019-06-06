package misk.hibernate

import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.hibernate.annotation.keyspace
import misk.jdbc.CowriteException
import misk.jdbc.FullScatterException
import misk.jdbc.TableScanException
import misk.jdbc.uniqueLong
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

  @Test
  fun crossShardTransactions() {
    val jg = transacter.save(
        DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
    val cf = transacter.save(
        DbActor("Carrie Fisher", null))

    val jp = transacter.save(
        DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
    val sw = transacter.createInSeparateShard(jp) {
      DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
    }

    assertThrows<CowriteException> {
      transacter.transaction { session ->
        val jpMovie = session.load(jp)
        val jgActor = session.load(jg)
        session.save(DbCharacter("Ian Malcolm", jpMovie, jgActor))
        val swMovie = session.load(sw)
        val cfActor = session.load(cf)
        session.save(DbCharacter("Leia Organa", swMovie, cfActor))
      }
    }
    assertThrows<CowriteException> {
      transacter.transaction { session ->
        val jpEntity = session.load(jp)
        jpEntity.release_date = LocalDate.now()
        val swEntity = session.load(sw)
        swEntity.release_date = LocalDate.now()
      }
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

    assertThrows<CowriteException> {
      transacter.transaction { session ->
        session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
        session.save(DbCharacter("Leia Organa", session.load(sw), session.load(cf)))
      }
    }
    assertThrows<CowriteException> {
      transacter.transaction { session ->
        val jpEntity = session.load(jp)
        jpEntity.release_date = LocalDate.now()
        val swEntity = session.load(sw)
        swEntity.release_date = LocalDate.now()
      }
    }
  }

  @Test
  fun crossShardQueriesAreDetected() {
    assertThrows<FullScatterException> {
      transacter.transaction { session ->
        queryFactory.newQuery<MovieQuery>()
            .releaseDateBefore(LocalDate.of(1977, 6, 15))
            .list(session)
      }
    }
  }

  @Test
  fun tableScansDetected() {
    transacter.transaction { session ->
      session.withoutChecks(Check.FULL_SCATTER, Check.COWRITE) {
        val cf = session.save(
            DbActor("Carrie Fisher", null))
        val sw = session.save(
            DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
        session.save(DbCharacter("Leia Organa", session.load(sw), session.load(cf)))

        assertThrows<TableScanException> {
          session.useConnection { c ->
            c.prepareStatement("SELECT COUNT(*) FROM characters WHERE name = ?").use { s ->
              s.setString(1, "Leia Organa")
              s.executeQuery().uniqueLong()
            }
          }
        }
        // And we can disable the check too
        session.withoutChecks(Check.TABLE_SCAN) {
          session.useConnection { c ->
            c.prepareStatement("SELECT COUNT(*) FROM characters WHERE name = ?").use { s ->
              s.setString(1, "Leia Organa")
              s.executeQuery().uniqueLong()
            }
          }
        }
      }
    }
  }

  @Test
  fun crossShardQueriesDetectorCanBeDisabled() {
    transacter.transaction { session ->
      session.withoutChecks {
        queryFactory.newQuery<MovieQuery>()
            .releaseDateBefore(LocalDate.of(1977, 6, 15))
            .list(session)
      }
    }
  }
}

private fun <T : DbEntity<T>> Transacter.save(entity: T): Id<T> = transaction { it.save(entity) }

fun <T : DbRoot<T>> Transacter.createInSeparateShard(id: Id<T>, factory: (Session) -> T): Id<T> =
    transaction { it.createInSeparateShard(id) { factory(it) } }

fun <T : DbRoot<T>> Transacter.createInSameShard(id: Id<T>, factory: (Session) -> T): Id<T> =
    transaction { it.createInSameShard(id) { factory(it) } }

fun <T : DbRoot<T>> Session.createInSeparateShard(
  id: Id<T>,
  factory: () -> T
): Id<T> {
  val sw = createUntil(factory) { newId ->
    newId.shard(this) != id.shard(this)
  }
  return sw
}

fun <T : DbRoot<T>> Session.createInSameShard(
  id: Id<T>,
  factory: () -> T
): Id<T> {
  val sw = createUntil(factory) { newId ->
    newId.shard(this) == id.shard(this)
  }
  return sw
}

class NotThereYetException : RuntimeException()

fun <T : DbRoot<T>> Session.createUntil(
  factory: () -> T,
  condition: (Id<T>) -> Boolean
): Id<T> = retry(10, FlatBackoff()) {
  val newId = save(factory())
  if (!condition(newId)) {
    throw NotThereYetException()
  }
  newId
}

fun <T : DbRoot<T>> Id<T>.shard(session: Session): Shard {
  val keyspace = javaClass.getAnnotation(misk.hibernate.annotation.Keyspace::class.java)
  val shards = session.shards(keyspace.keyspace()).plus(Shard.SINGLE_SHARD)
  return shards.find { it.contains(this.shardKey()) }!!
}
