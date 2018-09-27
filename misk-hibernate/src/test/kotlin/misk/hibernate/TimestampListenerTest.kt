package misk.hibernate

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
class TimestampListenerTest {
  @MiskTestModule
  val module = MoviesTestModule(disableCrossShardQueryDetector = true)

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var clock: FakeClock

  @Test
  fun timestampsInitializedOnInsert() {
    val createdAt = clock.instant()
    transacter.transaction { session ->
      val movie = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))
      session.save(movie)
      assertThat(movie.created_at).isEqualTo(createdAt)
      assertThat(movie.updated_at).isEqualTo(createdAt)
    }
  }

  @Test
  fun timestampsUpdatedOnUpdate() {
    val createdAt = clock.instant()
    transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1993, 6, 9)))
    }

    clock.add(5, TimeUnit.SECONDS)

    val updatedAt = clock.instant()
    transacter.transaction { session ->
      val movie = queryFactory.newQuery<MovieQuery>().uniqueResult(session)!!
      movie.name = "A New Hope"
      session.hibernateSession.update(movie) // TODO(jwilson): expose session.update() directly.
      session.hibernateSession.flush() // TODO(jwilson): expose session.flush() directly.
      assertThat(movie.created_at).isEqualTo(createdAt)
      assertThat(movie.updated_at).isEqualTo(updatedAt)
    }
  }
}
