package misk.hibernate

import io.opentracing.mock.MockTracer
import misk.exceptions.UnauthorizedException
import misk.jdbc.DataSourceType
import misk.jdbc.uniqueString
import misk.logging.LogCollector
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.test.assertFailsWith

abstract class TransacterTest {
  @Inject @Movies lateinit var transacter: Transacter
  @Inject @MoviesReader lateinit var readerTransacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var tracer: MockTracer
  @Inject lateinit var logCollector: LogCollector

  @Test
  fun happyPath() {
    createTestData()

    if (hasDateBug()) {
      return
    }

    // Query that data.
    transacter.transaction { session ->
      val ianMalcolm = queryFactory.newQuery<CharacterQuery>()
          .allowFullScatter().allowTableScan()
          .name("Ian Malcolm")
          .uniqueResult(session)!!
      assertThat(ianMalcolm.actor?.name).isEqualTo("Jeff Goldblum")
      assertThat(ianMalcolm.movie.name).isEqualTo("Jurassic Park")

      val lauraDernMovies = queryFactory.newQuery<CharacterQuery>()
          .allowFullScatter().allowTableScan()
          .actorName("Laura Dern")
          .listAsMovieNameAndReleaseDate(session)
      assertThat(lauraDernMovies).containsExactlyInAnyOrder(
          NameAndReleaseDate("Star Wars", LocalDate.of(1977, 5, 25)),
          NameAndReleaseDate("Jurassic Park", LocalDate.of(1993, 6, 9)))

      val actorsInOldMovies = queryFactory.newQuery<CharacterQuery>()
          .allowFullScatter().allowTableScan()
          .movieReleaseDateBefore(LocalDate.of(1980, 1, 1))
          .listAsActorAndReleaseDate(session)
      assertThat(actorsInOldMovies).containsExactlyInAnyOrder(
          ActorAndReleaseDate("Laura Dern", LocalDate.of(1977, 5, 25)),
          ActorAndReleaseDate("Carrie Fisher", LocalDate.of(1977, 5, 25)))
    }

    // Query with replica reads.
    transacter.replicaRead { session ->
      val query = queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm")
      val ianMalcolm = query.uniqueResult(session)!!
      assertThat(ianMalcolm.actor?.name).isEqualTo("Jeff Goldblum")
      assertThat(ianMalcolm.movie.name).isEqualTo("Jurassic Park")

      // Shard targeting works.
      val shard = ianMalcolm.rootId.shard(session)
      session.target(shard) {
        assertThat(query.uniqueResult(session)!!.actor?.name).isEqualTo("Jeff Goldblum")
      }
    }
    readerTransacter.transaction { session ->
      val query = queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm")
      val ianMalcolm = query.uniqueResult(session)!!
      assertThat(ianMalcolm.actor?.name).isEqualTo("Jeff Goldblum")
      assertThat(ianMalcolm.movie.name).isEqualTo("Jurassic Park")

      // Shard targeting works.
      val shard = ianMalcolm.rootId.shard(session)
      session.target(shard) {
        assertThat(query.uniqueResult(session)!!.actor?.name).isEqualTo("Jeff Goldblum")
      }
    }

    transacter.failSafeRead { session ->
      val query = queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm")
      val ianMalcolm = query.uniqueResult(session)!!
      assertThat(ianMalcolm.actor?.name).isEqualTo("Jeff Goldblum")
      assertThat(ianMalcolm.movie.name).isEqualTo("Jurassic Park")

      // Shard targeting works.
      val shard = ianMalcolm.rootId.shard(session)
      session.target(shard) {
        assertThat(query.uniqueResult(session)!!.actor?.name).isEqualTo("Jeff Goldblum")
      }
    }

    // Delete some data.
    transacter.transaction { session ->
      val ianMalcolm = queryFactory.newQuery<CharacterQuery>()
          .allowFullScatter().allowTableScan()
          .name("Ian Malcolm")
          .uniqueResult(session)!!

      session.delete(ianMalcolm)

      val afterDelete = queryFactory.newQuery<CharacterQuery>()
          .allowFullScatter().allowTableScan()
          .name("Ian Malcolm")
          .uniqueResult(session)
      assertThat(afterDelete).isNull()
    }
  }

  // TODO TiDB are working on fixing this bug: https://github.com/pingcap/tidb/issues/13791
  private fun hasDateBug() = transacter.config().type == DataSourceType.TIDB

  private fun createTestData() {
    // Insert some movies, characters and actors.
    transacter.allowCowrites().transaction { session ->
      val jp = session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      val sw = session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
      val lx = session.save(DbMovie("Luxo Jr.", LocalDate.of(1986, 8, 17)))
      assertThat(setOf(jp, sw)).hasSize(2) // Uniqueness check.

      val ld = session.save(DbActor("Laura Dern", LocalDate.of(1967, 2, 10)))
      val jg = session.save(DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
      val cf = session.save(DbActor("Carrie Fisher", null))
      assertThat(setOf(ld, jg, cf)).hasSize(3) // Uniqueness check.

      val ah = session.save(DbCharacter("Amilyn Holdo", session.load(sw), session.load(ld)))
      val es = session.save(DbCharacter("Ellie Sattler", session.load(jp), session.load(ld)))
      val im = session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
      val lo = session.save(DbCharacter("Leia Organa", session.load(sw), session.load(cf)))
      val lj = session.save(DbCharacter("Luxo Jr.", session.load(lx), null))
      assertThat(setOf(ah, es, im, lo, lj)).hasSize(5) // Uniqueness check.
    }
  }

  @Test
  fun `saves dates properly`() {
    if (hasDateBug()) {
      return
    }

    val releaseDate = LocalDate.of(1993, 6, 9)
    val jp = transacter.transaction { session ->
      session.save(DbMovie("Jurassic Park", releaseDate))
    }
    transacter.transaction { session ->
      assertThat(session.load(jp).release_date).isEqualTo(releaseDate)
    }
  }

  @Test
  fun `cant nest replica reads`() {
    createTestData()

    transacter.replicaRead { session ->
      queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm").uniqueResult(session)!!

      assertThrows<IllegalStateException> {
        transacter.replicaRead { session ->
          queryFactory.newQuery<CharacterQuery>()
              .allowTableScan()
              .name("Ian Malcolm").uniqueResult(session)!!
        }
      }
    }
  }

  @Test
  fun `can do comparison query on ids`() {
    createTestData()

    transacter.replicaRead { session ->
      val cb = session.hibernateSession.criteriaBuilder
      val cr = cb.createQuery(DbCharacter::class.java)
      val root = cr.from(DbCharacter::class.java)
      val idProperty = root.get<Id<DbCharacter>>("id")
      val characters = session.hibernateSession.createQuery(
          cr.where(cb.greaterThan(idProperty, Id(0)))
              .orderBy(cb.asc(idProperty))
      ).resultList
      assertThat(characters).hasSize(5)
    }
  }

  @Test
  fun `shard targeting`() {
    // This test only makes sense with Vitess
    if (!transacter.config().type.isVitess) {
      return
    }

    val jp = transacter.save(
        DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
    val sw = transacter.createInSeparateShard(jp) {
      DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
    }

    // Shard targeting works in replica reads
    transacter.replicaRead { session ->
      session.target(jp.shard(session)) {
        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Jurassic Park").uniqueResult(session)
        ).isNotNull()

        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(sw.shard(session)) {
        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Star Wars").uniqueResult(session)
        ).isNotNull()
      }
    }

    // Shard targeting works in fail safe reads
    transacter.failSafeRead { session ->
      session.target(jp.shard(session)) {
        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Jurassic Park").uniqueResult(session)
        ).isNotNull()

        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(sw.shard(session)) {
        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Star Wars").uniqueResult(session)
        ).isNotNull()
      }
    }

    // Shard targeting works in transactions
    transacter.transaction { session ->
      session.target(jp.shard(session)) {
        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Jurassic Park").uniqueResult(session)
        ).isNotNull()

        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Star Wars").uniqueResult(session)
        ).isNull()
      }

      session.target(sw.shard(session)) {
        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Jurassic Park").uniqueResult(session)
        ).isNull()

        assertThat(
            queryFactory.newQuery<MovieQuery>().allowTableScan()
                .name("Star Wars").uniqueResult(session)
        ).isNotNull()
      }
    }
  }

  @Test
  fun `can run consecutive replica reads and transactions`() {
    createTestData()

    transacter.replicaRead { session ->
      if (transacter.config().type.isVitess) {
        // Make sure this doesn't trigger a transaction
        val target = session.useConnection { c ->
          c.createStatement().use {
            it.executeQuery("SHOW VITESS_TARGET").uniqueString()
          }
        }
        assertThat(target).isEqualTo("@replica")
      }

      queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm").uniqueResult(session)!!
    }

    transacter.replicaRead { session ->
      queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm").uniqueResult(session)!!
    }

    transacter.transaction { session ->
      if (transacter.config().type.isVitess) {
        val target = session.useConnection { c ->
          c.createStatement().use {
            it.executeQuery("SHOW VITESS_TARGET").uniqueString()
          }
        }
        assertThat(target).isEqualTo("@master")
      }

      val character = queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm").uniqueResult(session)!!
      character.name = "Ian Malcolm 2"
    }

    transacter.replicaRead { session ->
      assertThat(queryFactory.newQuery<CharacterQuery>()
          .allowTableScan()
          .name("Ian Malcolm 2").uniqueResult(session)).isNotNull()
    }
  }

  @Test
  fun loadOrNullReturnsEntity() {
    transacter.transaction { session ->
      val ld = session.save(DbActor("Laura Dern", LocalDate.of(1967, 2, 10)))
      assertThat(session.loadOrNull(ld)).isNotNull()
    }
  }

  @Test
  fun loadOrNullMissingEntityReturnsNull() {
    transacter.transaction { session ->
      assertThat(session.loadOrNull(Id<DbActor>(123))).isNull()
    }
  }

  @Test
  fun exceptionCausesTransactionToRollback() {
    assertFailsWith<UnauthorizedException> {
      transacter.transaction { session ->
        session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
        assertThat(queryFactory.newQuery<MovieQuery>()
            .allowFullScatter().allowTableScan().list(session)).isNotEmpty()
        throw UnauthorizedException("boom!")
      }
    }
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<MovieQuery>().allowFullScatter().allowTableScan()
          .list(session)).isEmpty()
    }
  }

  @Test
  fun constraintViolationCausesTransactionToRollback() {
    // Uniqueness constraints aren't reliably enforced on Vitess
    assumeTrue(!transacter.config().type.isVitess)

    transacter.transaction { session ->
      session.save(DbMovie("Cinderella", LocalDate.of(1950, 3, 4)))
    }
    assertFailsWith<ConstraintViolationException> {
      transacter.transaction { session ->
        session.save(DbMovie("Beauty and the Beast", LocalDate.of(1991, 11, 22)))
        session.save(DbMovie("Cinderella", LocalDate.of(2015, 3, 13)))
      }
    }
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<MovieQuery>().allowTableScan().list(session)).hasSize(1)
    }
  }

  @Test
  fun inTransaction() {
    assertThat(transacter.inTransaction).isFalse()

    transacter.transaction {
      assertThat(transacter.inTransaction).isTrue()
    }

    assertThat(transacter.inTransaction).isFalse()
  }

  @Test
  fun noFailSafeReadInTransaction() {
    assertThat(transacter.inTransaction).isFalse()

    transacter.transaction {
      assertThat(transacter.inTransaction).isTrue()

      assertFailsWith<IllegalStateException> {
        transacter.failSafeRead {
          queryFactory.newQuery<MovieQuery>().allowTableScan()
              .name("Jurassic Park").uniqueResult(it)
        }
      }
    }

    assertThat(transacter.inTransaction).isFalse()
  }

  @Test
  fun nestedTransactionUnsupported() {
    val exception = assertFailsWith<IllegalStateException> {
      transacter.transaction {
        transacter.transaction {
        }
      }
    }
    assertThat(exception).hasMessage("Attempted to start a nested session")
  }

  @Test
  fun nestedTransactionUnsupportedWithDerivativeTransacter() {
    val exception = assertFailsWith<IllegalStateException> {
      transacter.transaction {
        transacter.noRetries().transaction {
        }
      }
    }
    assertThat(exception).hasMessage("Attempted to start a nested session")
  }

  @Test
  fun transactionSucceedsImmediately() {
    val callCount = AtomicInteger()
    val result = transacter.transaction {
      callCount.getAndIncrement()
      "success"
    }
    assertThat(callCount.get()).isEqualTo(1)
    assertThat(result).isEqualTo("success")
  }

  @Test
  fun transactionSucceedsAfterRetry() {
    val callCount = AtomicInteger()
    transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
      assertThat(queryFactory.newQuery<MovieQuery>().allowFullScatter().allowTableScan()
          .list(session)).isNotEmpty()

      if (callCount.getAndIncrement() == 0) throw RetryTransactionException()
    }
    assertThat(callCount.get()).isEqualTo(2)
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<MovieQuery>().allowFullScatter().allowTableScan()
          .list(session)).hasSize(1)
    }
  }

  @Test
  fun nonRetryableExceptionsNotRetried() {
    val callCount = AtomicInteger()
    assertFailsWith<NonRetryableException> {
      transacter.transaction {
        callCount.getAndIncrement()
        throw NonRetryableException()
      }
    }
    assertThat(callCount.get()).isEqualTo(1)
  }

  @Test
  fun noRetriesFailsImmediately() {
    val callCount = AtomicInteger()
    assertFailsWith<RetryTransactionException> {
      transacter.noRetries().transaction {
        callCount.getAndIncrement()
        throw RetryTransactionException()
      }
    }
    assertThat(callCount.get()).isEqualTo(1)
  }

  @Test
  fun readOnlyWontSave() {
    assertFailsWith<IllegalStateException> {
      transacter.readOnly().transaction { session ->
        session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
      }
    }
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<MovieQuery>().allowFullScatter().allowTableScan()
          .list(session)).isEmpty()
    }
  }

  @Test
  fun readOnlyWontUpdate() {
    val id: Id<DbMovie> = transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    transacter.readOnly().transaction { session ->
      val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
      movie!!.name = "Not Star Wars"
    }

    transacter.transaction { session ->
      val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
      assertThat(movie!!.name).isEqualTo("Star Wars")
    }
  }

  @Test
  fun readOnlyWontDelete() {
    val id: Id<DbMovie> = transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    assertFailsWith<IllegalStateException> {
      transacter.readOnly().transaction { session ->
        val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
        session.delete(movie!!)
      }
    }

    transacter.transaction { session ->
      val movie: DbMovie? = queryFactory.newQuery<MovieQuery>().id(id).uniqueResult(session)
      assertThat(movie).isNotNull()
    }
  }

  @Test
  fun preCommitHooksCalledPriorToCommit() {
    val preCommitHooksTriggered = mutableListOf<String>()
    lateinit var cid: Id<DbMovie>
    lateinit var bbid: Id<DbMovie>
    lateinit var swid: Id<DbMovie>

    transacter.allowCowrites().transaction { session ->
      session.onPreCommit {
        preCommitHooksTriggered.add("first")
        cid = session.save(DbMovie("Cinderella", LocalDate.of(1950, 3, 4)))
      }

      session.onPreCommit {
        preCommitHooksTriggered.add("second")
        bbid = session.save(DbMovie("Beauty and the Beast", LocalDate.of(1991, 11, 22)))
      }

      swid = session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))

      session.onPreCommit {
        preCommitHooksTriggered.add("third")
      }
    }

    // Pre-commit hooks should be triggered in the order in which they were registered...
    assertThat(preCommitHooksTriggered).containsExactly("first", "second", "third")

    // ...and should have been able to take actions on the session since it has not yet committed
    assertThat(transacter.transaction { it.load(cid) }.name).isEqualTo("Cinderella")
    assertThat(transacter.transaction { it.load(swid) }.name).isEqualTo("Star Wars")
    assertThat(transacter.transaction { it.load(bbid) }.name).isEqualTo("Beauty and the Beast")
  }

  @Test
  fun errorInPreCommitHookCausesTransactionRollback() {
    val preCommitHooksTriggered = mutableListOf<String>()
    lateinit var swid: Id<DbMovie>

    assertThrows<IllegalStateException> {
      transacter.transaction { session ->
        session.onPreCommit {
          preCommitHooksTriggered.add("first")
          throw IllegalStateException("bad things happened")
        }

        session.onPreCommit {
          preCommitHooksTriggered.add("second")
        }

        swid = session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))

        session.onPreCommit {
          preCommitHooksTriggered.add("third")
        }
      }
    }

    // Only the first hook should be called, the others should be skipped since the first failed...
    assertThat(preCommitHooksTriggered).containsExactly("first")

    // ...and the transaction should have been rolled back
    assertThat(transacter.transaction {
      queryFactory.newQuery<MovieQuery>().id(swid).list(it)
    }).isEmpty()
  }

  @Test
  fun postCommitHooksCalledOnCommit() {
    val postCommitHooksTriggered = mutableListOf<String>()
    lateinit var swid: Id<DbMovie>

    transacter.transaction { session ->
      session.onPostCommit { postCommitHooksTriggered.add("first") }
      session.onPostCommit { postCommitHooksTriggered.add("second") }

      swid = session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))

      session.onPostCommit { postCommitHooksTriggered.add("third") }
    }

    // Post-commit hooks should be triggered in the order in which they were registered...
    assertThat(postCommitHooksTriggered).containsExactly("first", "second", "third")

    // ...and the transaction should have completed
    assertThat(transacter.transaction { it.load(swid) }.name).isEqualTo("Star Wars")
  }

  @Test
  fun postCommitHooksNotCalledOnRollback() {
    val postCommitHooksTriggered = mutableListOf<String>()

    assertThrows<IllegalStateException> {
      transacter.transaction { session ->
        session.onPostCommit { postCommitHooksTriggered.add("first") }
        session.onPostCommit { postCommitHooksTriggered.add("second") }
        throw IllegalStateException("bad things happened here")
      }
    }

    assertThat(postCommitHooksTriggered).isEmpty()
  }

  @Test
  fun errorInPostCommitHookDoesNotRollback() {
    val postCommitHooksTriggered = mutableListOf<String>()
    lateinit var swid: Id<DbMovie>

    val cause = assertThrows<PostCommitHookFailedException> {
      transacter.transaction { session ->
        session.onPostCommit {
          postCommitHooksTriggered.add("first")
          throw IllegalStateException("first hook failed")
        }
        session.onPostCommit {
          postCommitHooksTriggered.add("second")
        }

        swid = session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))

        session.onPostCommit { postCommitHooksTriggered.add("third") }
      }
    }.cause

    assertThat(cause).hasMessage("first hook failed")
    assertThat(cause).isInstanceOf(IllegalStateException::class.java)

    // Only the first hook should complete - the others should be the skipped due to error...
    assertThat(postCommitHooksTriggered).containsExactly("first")

    // ...but the transaction itself should have completed
    assertThat(transacter.transaction { it.load(swid) }.name).isEqualTo("Star Wars")
  }

  @Test
  fun sessionCloseHookWorks() {
    val logs = mutableListOf<String>()

    transacter.transaction { session ->
      session.onSessionClose {
        assertThat(transacter.inTransaction).isFalse()
        logs.add("first")
      }
      session.onSessionClose {
        assertThat(transacter.inTransaction).isFalse()
        logs.add("second")
      }
    }

    assertThat(logs).containsExactly("first", "second")
  }

  @Test
  fun exceptionRaisedFromSessionCloseHook() {
    val cause = assertThrows<IllegalStateException> {
      transacter.transaction { session ->
        session.onSessionClose {
          throw IllegalStateException("hook failed")
        }
      }
    }

    assertThat(cause).hasMessage("hook failed")
    assertThat(cause).isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun sessionCloseHookInvokedEvenOnRollback() {
    val logs = mutableListOf<String>()

    assertThrows<NonRetryableException> {
      transacter.transaction { session ->
        session.onSessionClose {
          logs.add("hook invoked")
        }
        throw NonRetryableException()
      }
    }

    assertThat(logs).containsExactly("hook invoked")
  }

  @Test
  fun retriesIncludeConnectionReuse() {
    logCollector.takeMessages()

    val callCount = AtomicInteger()
    transacter.retries(3).transaction {
      if (callCount.getAndIncrement() < 2) throw RetryTransactionException()
    }

    val logs = logCollector.takeMessages(RealTransacter::class)
    assertThat(logs).hasSize(3)
    assertThat(logs[0]).matches("Movies recoverable transaction exception " +
        "\\(attempt 1\\), will retry after a PT.*S delay")
    assertThat(logs[1]).matches("Movies recoverable transaction exception " +
        "\\(attempt 2, same connection\\), will retry after a PT.*S delay")
    assertThat(logs[2]).matches(
        "retried Movies transaction succeeded \\(attempt 3, same connection\\)")
  }

  @Test
  fun concurrentUpdates() {
    transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1975, 5, 25)))
    }

    val txnEntryLatch = CountDownLatch(2)
    val txnExitLatch = CountDownLatch(2)

    val asyncWrite = Callable {
      transacter.transaction { session ->
        txnEntryLatch.countDown()
        val movie = queryFactory.newQuery(MovieQuery::class)
            .name("Star Wars")
            .uniqueResult(session)!!
        movie.release_date = LocalDate.of(movie.release_date!!.year + 1, 5, 25)
        txnExitLatch.countDown()
      }
    }

    val futureResults = Executors.newFixedThreadPool(2)
        .invokeAll(listOf(asyncWrite, asyncWrite))
        .map { it.get() }

    assertThat(futureResults).hasSize(2)

    val movies = transacter.transaction { session ->
      queryFactory.newQuery(MovieQuery::class).list(session)
    }
    assertThat(movies.size).isEqualTo(1)
    assertThat(movies[0].release_date == LocalDate.of(1977, 5, 25))
  }

  class NonRetryableException : Exception()
}

@MiskTest(startService = true)
class MySQLTransacterTest : TransacterTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.MYSQL)
}

@MiskTest(startService = true)
class VitessMySQLTransacterTest : TransacterTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.VITESS_MYSQL)
}

@MiskTest(startService = true)
@Disabled // TODO these are flaky, trying to resolve that with Cockroach
class CockroachTransacterTest : TransacterTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.COCKROACHDB)
}

@MiskTest(startService = true)
class TidbTransacterTest : TransacterTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.TIDB)
}

@MiskTest(startService = true)
class PostgresqlTransacterTest : TransacterTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.POSTGRESQL)
}
