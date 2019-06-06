package misk.hibernate

import com.google.inject.util.Modules
import io.opentracing.mock.MockTracer
import misk.hibernate.QueryTracingSpanNames.Companion.DB_DELETE
import misk.hibernate.QueryTracingSpanNames.Companion.DB_INSERT
import misk.hibernate.QueryTracingSpanNames.Companion.DB_SELECT
import misk.hibernate.QueryTracingSpanNames.Companion.DB_UPDATE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class QueryTracingTest {
  @MiskTestModule
  val module = Modules.combine(
      MoviesTestModule()
  )

  @Inject @Movies private lateinit var transacter: Transacter
  @Inject private lateinit var queryFactory: Query.Factory
  @Inject private lateinit var mockTracer: MockTracer

  @Test
  fun selectTraced() {
    transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1993, 6, 9)))
    }

    transacter.transaction { session ->
      queryFactory.newQuery<MovieQuery>()
          .allowFullScatter().allowTableScan()
          .uniqueResult(session)!!
      assertThat(mockTracer.finishedSpans()).extracting("operationName")
          .contains(DB_SELECT)
    }
  }

  @Test
  fun insertTraced() {
    transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1993, 6, 9)))
    }
    assertThat(mockTracer.finishedSpans()).extracting("operationName")
        .contains(DB_INSERT)
  }

  @Test
  fun updateTraced() {
    transacter.transaction { session ->
      val movie = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))
      session.save(movie)
      movie.name = "Star Wars - The Last Jedi"
    }
    assertThat(mockTracer.finishedSpans()).extracting("operationName")
        .contains(DB_UPDATE)
  }

  @Test
  fun deleteTraced() {
    transacter.transaction { session ->
      val movie = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))
      session.save(movie)
      session.hibernateSession.flush()
      session.hibernateSession.remove(movie)
    }
    assertThat(mockTracer.finishedSpans()).extracting("operationName")
        .contains(DB_DELETE)
  }

  @Test
  fun multipleQueriesTraced() {
    transacter.transaction { session ->
      val movie = DbMovie("Star Wars", LocalDate.of(1993, 6, 9))
      session.save(movie)
      movie.name = "Star Wars - The Last Jedi"
      session.hibernateSession.flush()
      queryFactory.newQuery<MovieQuery>()
          .allowFullScatter().allowTableScan()
          .uniqueResult(session)!!
      session.hibernateSession.remove(movie)
    }
    assertThat(mockTracer.finishedSpans()).extracting("operationName")
        .contains(
            DB_INSERT,
            DB_UPDATE,
            DB_SELECT,
            DB_DELETE
        )
  }

  @Test
  fun multipleEntitysInSelect() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1993, 6, 9)))
      session.save(DbMovie("Star Wars - The Last Jedi", LocalDate.of(1993, 6, 9)))
      session.hibernateSession.flush()
      queryFactory.newQuery<MovieQuery>()
          .allowFullScatter().allowTableScan()
          .list(session)
    }
    assertThat(mockTracer.finishedSpans()).extracting("operationName")
        .containsOnlyOnce(DB_SELECT)
  }
}
