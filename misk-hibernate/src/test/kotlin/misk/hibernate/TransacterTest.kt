package misk.hibernate

import io.opentracing.mock.MockTracer
import io.opentracing.tag.Tags
import misk.exceptions.UnauthorizedException
import misk.hibernate.RealTransacter.Companion.APPLICATION_TRANSACTION_SPAN_NAME
import misk.hibernate.RealTransacter.Companion.DB_TRANSACTION_SPAN_NAME
import misk.hibernate.RealTransacter.Companion.TRANSACTER_SPAN_TAG
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
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
          ReflectionQueryFactoryTest.NameAndReleaseDate("Star Wars", LocalDate.of(1977, 5, 25)),
          ReflectionQueryFactoryTest.NameAndReleaseDate("Jurassic Park", LocalDate.of(1993, 6, 9)))

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
  fun committedTransactionsTraceSuccess() {
    tracer.reset()

    transacter.transaction {
      // No need to do anything
    }

    tracingAssertions()
  }

  @Test
  fun rolledbackTransactionsTraceError() {
    tracer.reset()

    assertFailsWith<NonRetryableException> {
      transacter.transaction {
        throw NonRetryableException()
      }
    }

    tracingAssertions()
  }

  fun tracingAssertions() {
    // Assert on span, implicitly asserting that it's complete by looking at finished spans
    assertThat(tracer.finishedSpans()).hasSize(2)
    assertThat(tracer.finishedSpans().get(0).operationName())
        .isEqualTo(DB_TRANSACTION_SPAN_NAME)
    assertThat(tracer.finishedSpans().get(0).tags())
        .containsEntry(Tags.COMPONENT.getKey(), TRANSACTER_SPAN_TAG)
    assertThat(tracer.finishedSpans().get(1).operationName())
        .isEqualTo(APPLICATION_TRANSACTION_SPAN_NAME)
    assertThat(tracer.finishedSpans().get(1).tags())
        .containsEntry(Tags.COMPONENT.getKey(), TRANSACTER_SPAN_TAG)

    // There should be no on-going span
    assertThat(tracer.activeSpan()).isNull()
  }

  interface CharacterQuery : Query<DbCharacter> {
    @Constraint("name")
    fun name(name: String): CharacterQuery

    @Constraint("actor.name")
    fun actorName(name: String): CharacterQuery

    @Constraint(path = "movie.release_date", operator = Operator.LT)
    fun movieReleaseDateBefore(upperBound: LocalDate): CharacterQuery

    @Select("movie")
    fun listAsMovieNameAndReleaseDate(session: Session): List<ReflectionQueryFactoryTest.NameAndReleaseDate>

    @Select
    fun listAsActorAndReleaseDate(session: Session): List<ActorAndReleaseDate>
  }

  data class NameAndReleaseDate(
    @Property("name") var movieName: String,
    @Property("release_date") var movieReleaseDate: LocalDate?
  ) : Projection

  data class ActorAndReleaseDate(
    @Property("actor.name") var actorName: String,
    @Property("movie.release_date") var movieReleaseDate: LocalDate?
  ) : Projection

  class NonRetryableException : Exception()
}
