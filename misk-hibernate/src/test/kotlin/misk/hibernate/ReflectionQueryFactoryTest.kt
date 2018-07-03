package misk.hibernate

import ch.qos.logback.classic.Level
import com.google.common.collect.Iterables.getOnlyElement
import misk.logging.LogCollector
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.assertThrows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class ReflectionQueryFactoryTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var logCollector: LogCollector

  @Test
  fun comparisonOperators() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))
    val m4 = NameAndReleaseDate("Rocky 4", LocalDate.of(2018, 1, 4))
    val m5 = NameAndReleaseDate("Rocky 5", LocalDate.of(2018, 1, 5))
    val m98 = NameAndReleaseDate("Rocky 98", null)
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))
      session.save(DbMovie(m4.name, m4.releaseDate))
      session.save(DbMovie(m5.name, m5.releaseDate))
      session.save(DbMovie(m98.name, m98.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThan(m3.releaseDate)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1, m2)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThanOrEqualTo(m3.releaseDate)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1, m2, m3)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateEqualTo(m3.releaseDate)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m3)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThanOrEqualTo(m3.releaseDate)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m3, m4, m5)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThan(m3.releaseDate)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m4, m5)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateNotEqualTo(m3.releaseDate)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1, m2, m4, m5)
    }
  }

  /** Comparisons with null always return an empty list. */
  @Test
  fun comparisonWithNull() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))
    val m4 = NameAndReleaseDate("Rocky 4", LocalDate.of(2018, 1, 4))
    val m5 = NameAndReleaseDate("Rocky 5", LocalDate.of(2018, 1, 5))
    val m98 = NameAndReleaseDate("Rocky 98", null)
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))
      session.save(DbMovie(m4.name, m4.releaseDate))
      session.save(DbMovie(m5.name, m5.releaseDate))
      session.save(DbMovie(m98.name, m98.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThan(null)
          .listAsNameAndReleaseDate(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThanOrEqualTo(null)
          .listAsNameAndReleaseDate(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateEqualTo(null)
          .listAsNameAndReleaseDate(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThanOrEqualTo(null)
          .listAsNameAndReleaseDate(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThan(null)
          .listAsNameAndReleaseDate(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateNotEqualTo(null)
          .listAsNameAndReleaseDate(session))
          .isEmpty()
    }
  }

  @Test
  fun nullOperators() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m98 = NameAndReleaseDate("Rocky 98", null)
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m98.name, m98.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateIsNull()
          .listAsNameAndReleaseDate(session))
          .containsExactly(m98, m99)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateIsNotNull()
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1, m2)
    }
  }

  @Test
  fun inOperator() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInVararg(m1.releaseDate, m3.releaseDate)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1, m3)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInCollection(listOf(m1.releaseDate, m3.releaseDate))
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1, m3)
    }
  }

  @Test
  fun inOperatorWithNull() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", null)

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInVararg(m1.releaseDate, null)
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInCollection(listOf(m1.releaseDate, null))
          .listAsNameAndReleaseDate(session))
          .containsExactly(m1)
    }
  }

  @Test
  fun inOperatorWithEmptyList() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInVararg()
          .listAsNameAndReleaseDate(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInCollection(listOf())
          .listAsNameAndReleaseDate(session))
          .isEmpty()
    }
  }

  @Test
  fun singleColumnProjection() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateEqualTo(m1.releaseDate)
          .uniqueName(session))
          .isEqualTo(m1.name)

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThanOrEqualTo(m2.releaseDate)
          .listAsNames(session))
          .containsExactly(m1.name, m2.name)
    }
  }

  @Test
  fun singleColumnProjectionIsEmpty() {
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .uniqueName(session))
          .isNull()

      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .listAsNames(session))
          .isEmpty()
    }
  }

  /**
   * This should be multiple tests but it takes a few seconds to insert many rows so we bundle them
   * all into one big test.
   */
  @Test
  fun rowResultCountWarning() {
    // 1900 rows is too small for the warning threshold (2000).
    transacter.transaction { session ->
      for (i in 1..1900) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>().list(session)).hasSize(1900)
    }
    assertThat(logCollector.takeMessages(loggerClass = ReflectionQuery::class)).isEmpty()

    // 2100 rows logs a warning.
    transacter.transaction { session ->
      for (i in 1901..2100) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>().list(session)).hasSize(2100)
    }
    assertThat(getOnlyElement(
        logCollector.takeMessages(loggerClass = ReflectionQuery::class, minLevel = Level.WARN))
    ).startsWith("Unbounded query returned 2100 rows.")

    // 3100 rows logs an error.
    transacter.transaction { session ->
      for (i in 2101..3100) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>().list(session)).hasSize(3100)
    }
    assertThat(getOnlyElement(
        logCollector.takeMessages(loggerClass = ReflectionQuery::class, minLevel = Level.ERROR))
    ).startsWith("Unbounded query returned 3100 rows.")

    // An explicit max row count suppresses the warning.
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 3200 }
          .list(session))
          .hasSize(3100)
    }
    assertThat(logCollector.takeMessages(loggerClass = ReflectionQuery::class)).isEmpty()

    // More than 10000 rows throws an exception.
    transacter.transaction { session ->
      for (i in 3101..10100) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    assertThat(assertThrows<IllegalStateException> {
      transacter.transaction { session ->
        queryFactory.newQuery<OperatorsMovieQuery>().list(session)
      }
    }).hasMessage("query truncated at 10001 rows")
  }

  @Test
  fun maxRowCountNotExceeded() {
    transacter.transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 4 }
          .listAsNames(session))
          .containsExactly("Rocky 1", "Rocky 2", "Rocky 3")
    }
  }

  @Test
  fun maxMaxRowCountEnforced() {
    val query = queryFactory.newQuery<OperatorsMovieQuery>()
    assertThat(assertThrows<IllegalArgumentException> {
      query.maxRows = 10_001
    }).hasMessage("out of range: 10001")
  }

  @Test
  fun maxRowCountTruncates() {
    transacter.transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    // List.
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 2 }
          .list(session)
          .map { it.name })
          .containsExactly("Rocky 1", "Rocky 2")
    }

    // List projection.
    transacter.transaction { session ->
      assertThat(queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 2 }
          .listAsNames(session))
          .containsExactly("Rocky 1", "Rocky 2")
    }
  }

  @Test
  fun uniqueResultFailsOnTooManyResults() {
    transacter.transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    // Unique result.
    assertThat(assertThrows<IllegalStateException> {
      transacter.transaction { session ->
        queryFactory.newQuery<OperatorsMovieQuery>().uniqueResult(session)
      }
    }).hasMessageContaining("query expected a unique result but was")

    // Unique result projection.
    assertThat(assertThrows<IllegalStateException> {
      transacter.transaction { session ->
        queryFactory.newQuery<OperatorsMovieQuery>().uniqueName(session)
      }
    }).hasMessageContaining("query expected a unique result but was")
  }

  interface OperatorsMovieQuery : Query<DbMovie> {
    @Constraint(path = "release_date", operator = Operator.LT)
    fun releaseDateLessThan(upperBound: LocalDate?): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.LE)
    fun releaseDateLessThanOrEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.EQ)
    fun releaseDateEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.GE)
    fun releaseDateGreaterThanOrEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.GT)
    fun releaseDateGreaterThan(upperBound: LocalDate?): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.NE)
    fun releaseDateNotEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.IN)
    fun releaseDateInVararg(vararg upperBounds: LocalDate?): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.IN)
    fun releaseDateInCollection(upperBounds: Collection<LocalDate?>): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.IS_NOT_NULL)
    fun releaseDateIsNotNull(): OperatorsMovieQuery

    @Constraint(path = "release_date", operator = Operator.IS_NULL)
    fun releaseDateIsNull(): OperatorsMovieQuery

    @Select
    fun listAsNameAndReleaseDate(session: Session): List<NameAndReleaseDate>

    @Select("name")
    fun uniqueName(session: Session): String?

    @Select("name")
    fun listAsNames(session: Session): List<String>
  }

  data class NameAndReleaseDate(
    @Property("name") var name: String,
    @Property("release_date") var releaseDate: LocalDate?
  ) : Projection
}
