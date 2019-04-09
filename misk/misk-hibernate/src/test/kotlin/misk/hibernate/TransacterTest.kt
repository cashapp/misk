package misk.hibernate

import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import misk.exceptions.UnauthorizedException
import misk.hibernate.RealTransacter.Companion.APPLICATION_TRANSACTION_SPAN_NAME
import misk.hibernate.RealTransacter.Companion.DB_BEGIN_SPAN_NAME
import misk.hibernate.RealTransacter.Companion.DB_COMMIT_SPAN_NAME
import misk.hibernate.RealTransacter.Companion.DB_ROLLBACK_SPAN_NAME
import misk.hibernate.RealTransacter.Companion.DB_TRANSACTION_SPAN_NAME
import misk.hibernate.RealTransacter.Companion.TRANSACTER_SPAN_TAG
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class TransacterTest {
  @MiskTestModule
  val module = MoviesTestModule(disableCrossShardQueryDetector = true)

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var tracer: MockTracer

  @Test
  fun happyPath() {
    // Insert some movies, characters and actors.
    transacter.transaction { session ->
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

    // Query that data.
    transacter.transaction { session ->
      val ianMalcolm = queryFactory.newQuery<CharacterQuery>()
          .name("Ian Malcolm")
          .uniqueResult(session)!!
      assertThat(ianMalcolm.actor?.name).isEqualTo("Jeff Goldblum")
      assertThat(ianMalcolm.movie.name).isEqualTo("Jurassic Park")

      val lauraDernMovies = queryFactory.newQuery<CharacterQuery>()
          .actorName("Laura Dern")
          .listAsMovieNameAndReleaseDate(session)
      assertThat(lauraDernMovies).containsExactlyInAnyOrder(
          NameAndReleaseDate("Star Wars", LocalDate.of(1977, 5, 25)),
          NameAndReleaseDate("Jurassic Park", LocalDate.of(1993, 6, 9)))

      val actorsInOldMovies = queryFactory.newQuery<CharacterQuery>()
          .movieReleaseDateBefore(LocalDate.of(1980, 1, 1))
          .listAsActorAndReleaseDate(session)
      assertThat(actorsInOldMovies).containsExactlyInAnyOrder(
          ActorAndReleaseDate("Laura Dern", LocalDate.of(1977, 5, 25)),
          ActorAndReleaseDate("Carrie Fisher", LocalDate.of(1977, 5, 25)))
    }
  }

  @Test
  fun exceptionCausesTransactionToRollback() {
    assertFailsWith<UnauthorizedException> {
      transacter.transaction { session ->
        session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
        assertThat(queryFactory.newQuery<MovieQuery>().list(session)).isNotEmpty()
        throw UnauthorizedException("boom!")
      }
    }
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<MovieQuery>().list(session)).isEmpty()
    }
  }

  @Test
  @Disabled("Uniqueness constraints aren't reliably enforced on Vitess")
  fun constraintViolationCausesTransactionToRollback() {
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
      assertThat(queryFactory.newQuery<MovieQuery>().list(session)).hasSize(1)
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
      assertThat(queryFactory.newQuery<MovieQuery>().list(session)).isNotEmpty()

      if (callCount.getAndIncrement() == 0) throw RetryTransactionException()
    }
    assertThat(callCount.get()).isEqualTo(2)
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<MovieQuery>().list(session)).hasSize(1)
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
      assertThat(queryFactory.newQuery<MovieQuery>().list(session)).isEmpty()
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
  fun committedTransactionsTraceSuccess() {
    tracer.reset()

    transacter.transaction {
      // No need to do anything
    }

    tracingAssertions(true)
  }

  @Test
  fun rolledbackTransactionsTraceError() {
    tracer.reset()

    assertFailsWith<NonRetryableException> {
      transacter.transaction {
        throw NonRetryableException()
      }
    }

    tracingAssertions(false)
  }

  @Test
  fun preCommitHooksCalledPriorToCommit() {
    val preCommitHooksTriggered = mutableListOf<String>()
    lateinit var cid: Id<DbMovie>
    lateinit var bbid: Id<DbMovie>
    lateinit var swid: Id<DbMovie>

    transacter.transaction { session ->
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
  fun sessionCloseHookInvokedEvenOnRollBak() {
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

  fun tracingAssertions(committed: Boolean) {
    // Assert on span, implicitly asserting that it's complete by looking at finished spans
    val orderedSpans = tracer.finishedSpans().sortedBy { it.context().spanId() }
    assertThat(orderedSpans).hasSize(4)

    assertThat(orderedSpans.get(0).operationName())
        .isEqualTo(APPLICATION_TRANSACTION_SPAN_NAME)
    assertThat(orderedSpans.get(0).tags())
        .containsEntry(Tags.COMPONENT.getKey(), TRANSACTER_SPAN_TAG)

    assertThat(orderedSpans.get(1).operationName())
        .isEqualTo(DB_TRANSACTION_SPAN_NAME)
    assertThat(orderedSpans.get(1).tags())
        .containsEntry(Tags.COMPONENT.getKey(), TRANSACTER_SPAN_TAG)

    assertThat(orderedSpans.get(2).operationName())
        .isEqualTo(DB_BEGIN_SPAN_NAME)
    assertThat(orderedSpans.get(2).tags())
        .containsEntry(Tags.COMPONENT.getKey(), TRANSACTER_SPAN_TAG)

    if (committed) {
      assertThat(orderedSpans.get(3).operationName()).isEqualTo(DB_COMMIT_SPAN_NAME)
    } else {
      assertThat(orderedSpans.get(3).operationName()).isEqualTo(DB_ROLLBACK_SPAN_NAME)
    }
    assertThat(orderedSpans.get(3).tags())
        .containsEntry(Tags.COMPONENT.getKey(), TRANSACTER_SPAN_TAG)

    // There should be no on-going span
    assertThat(tracer.activeSpan()).isNull()
  }

  class NonRetryableException : Exception()
}
