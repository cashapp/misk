package misk.hibernate

import misk.backoff.FlatBackoff
import misk.backoff.retry
import misk.hibernate.annotation.keyspace
import misk.jdbc.Check
import misk.jdbc.TableScanException
import misk.jdbc.uniqueLong
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.CowriteException
import misk.vitess.Shard
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.UndeclaredThrowableException
import java.time.LocalDate
import javax.inject.Inject

/**
 * Verifies that we're constraining a few things that makes apps hard to scale out.
 */
@MiskTest(startService = true)
class VitessScaleSafetyTest {
  @MiskTestModule
  val module = MoviesTestModule(scaleSafetyChecks = true)

  @Inject @Movies lateinit var sessionFactory: SessionFactory
  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun crossShardTransactions() {
    val jg = transacter.save(
      DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22))
    )
    val cf = transacter.save(DbActor("Carrie Fisher"))

    val jp = transacter.save(
      DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
    )
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
      DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22))
    )
    val cf = transacter.save(DbActor("Carrie Fisher"))

    val jp = transacter.save(
      DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
    )
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
  fun concurrentIndependentTransactions() {
    // misk.hibernate.Transacter.transaction prevents nested transactions, and with good reason.
    // However, in some very specific, Misk-internal cases, org.hibernate.SessionFactory can be used
    // to spin up a new, independent transaction while another is ongoing.
    val jg = transacter.save(
      DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22))
    )
    val cf = transacter.save(DbActor("Carrie Fisher"))
    val ld = transacter.save(DbActor("Laura Dern"))
    val sn = transacter.save(DbActor("Sam Neill"))

    val jp = transacter.save(
      DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
    )
    val sw = transacter.save(
      DbMovie("Star Wars: The Last Jedi", LocalDate.of(2017, 12, 15))
    )

    transacter.transaction { session ->
      session.save(DbCharacter("Dr. Ian Malcolm", session.load(jp), session.load(jg)))

      // The Hibernate session can operate on a different entity group. (This would normally be
      // done in some sort of library call, where the Misk session is intentionally omitted.)
      hibernateTransaction { hSession ->
        hSession.save(
          DbCharacter(
            "Leia Organa",
            hSession.load(DbMovie::class.java, sw), hSession.load(DbActor::class.java, cf)
          )
        )
      }
    }
    assertEquals(setOf("Dr. Ian Malcolm"), movieCharacterNames(jp))
    assertEquals(setOf("Leia Organa"), movieCharacterNames(sw))

    // Regardless, one Hibernate session still cannot operate on two entity groups.
    assertThrows<CowriteException> {
      hibernateTransaction { hSession ->
        hSession.save(
          DbCharacter(
            "Dr. Ellie Sattler",
            hSession.load(DbMovie::class.java, jp), hSession.load(DbActor::class.java, ld)
          )
        )
        hSession.save(
          DbCharacter(
            "Vice Admiral Holdo",
            hSession.load(DbMovie::class.java, sw), hSession.load(DbActor::class.java, ld)
          )
        )
      }
    }
    assertEquals(setOf("Dr. Ian Malcolm"), movieCharacterNames(jp))
    assertEquals(setOf("Leia Organa"), movieCharacterNames(sw))

    // Further, we should still detect cowrites in a Misk session even if a Hibernate session
    // is used in the middle of it, and that Hibernate session is completely independent of any
    // other transaction.
    assertThrows<CowriteException> {
      transacter.transaction { session ->
        session.save(DbCharacter("Dr. Ellie Sattler", session.load(jp), session.load(ld)))

        hibernateTransaction { hSession ->
          hSession.save(
            DbCharacter(
              "Dr. Alan Grant",
              hSession.load(DbMovie::class.java, jp), hSession.load(DbActor::class.java, sn)
            )
          )
        }

        session.save(DbCharacter("Vice Admiral Holdo", session.load(sw), session.load(ld)))
      }
    }
    assertEquals(setOf("Dr. Ian Malcolm", "Dr. Alan Grant"), movieCharacterNames(jp))
    assertEquals(setOf("Leia Organa"), movieCharacterNames(sw))
  }

  @Test
  fun crossShardQueriesAreDetected() {
    assertThrows<UndeclaredThrowableException> {
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
          DbActor("Carrie Fisher", null)
        )
        val sw = session.save(
          DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
        )
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

  private fun movieCharacterNames(sw: Id<DbMovie>) = transacter.transaction { session ->
    queryFactory.newQuery<CharacterQuery>().movieId(sw).list(session).map { it.name }.toSet()
  }

  private fun <R> hibernateTransaction(block: (hSession: org.hibernate.Session) -> R): R =
    sessionFactory.openSession().use { hSession ->
      val transaction = hSession.beginTransaction()
      try {
        val result = block(hSession)
        transaction.commit()
        return result
      } catch (e: Throwable) {
        try {
          if (transaction.isActive) transaction.rollback()
        } catch (suppressed: Exception) {
          e.addSuppressed(suppressed)
        }
        throw e
      }
    }
}

fun <T : DbEntity<T>> Transacter.save(entity: T): Id<T> = transaction { it.save(entity) }

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
  val shards = session.shards(keyspace.keyspace()).plus(Shard.SINGLE_SHARD)
  return shards.find { it.contains(this.shardKey()) }!!
}
