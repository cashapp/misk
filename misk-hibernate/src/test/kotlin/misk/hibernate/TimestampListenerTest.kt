package misk.hibernate

import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.time.FakeClock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

abstract class TimestampListenerTest {
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
      val movie = queryFactory.newQuery<MovieQuery>()
        .allowFullScatter().allowTableScan()
        .uniqueResult(session)!!
      movie.name = "A New Hope"
      session.hibernateSession.update(movie) // TODO(jwilson): expose session.update() directly.
      session.hibernateSession.flush() // TODO(jwilson): expose session.flush() directly.
      assertThat(movie.created_at).isEqualTo(createdAt)
      assertThat(movie.updated_at).isEqualTo(updatedAt)
    }
  }

  @Test
  fun utcTimezone() {
    val movie = transacter.transaction { session ->
      val movie = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))
      session.save(movie)
      movie
    }
    transacter.transaction { session ->
      session.useConnection { c ->
        // Use raw SQL in order to verify the actual value in the DB. If Hibernate is used it
        // would map back into an Instant correctly using whatever TZ is configured.
        c.prepareStatement("select created_at from movies where id = ?").use { s ->
          s.setLong(1, movie.id.id)
          s.executeQuery().use { rs ->
            rs.next()
            assertThat(rs.getTimestamp(1).time)
              .isEqualTo(clock.instant().toEpochMilli())
          }
        }
      }
    }
  }
}

@MiskTest(startService = true)
class MySQLTimestampListenerTest : TimestampListenerTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.MYSQL)
}

@MiskTest(startService = true)
class VitessMySQLTimestampListenerTest : TimestampListenerTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.VITESS_MYSQL)
}

@MiskTest(startService = true)
class PostgreSQLTimestampListenerTest : TimestampListenerTest() {
  @MiskTestModule
  val module = MoviesTestModule(DataSourceType.POSTGRESQL)
}
