package misk.hibernate

import ch.qos.logback.classic.Level
import com.google.common.collect.Iterables.getOnlyElement
import misk.hibernate.Operator.LT
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import java.time.Duration.ofSeconds
import java.time.LocalDate
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class ReflectionQueryFactoryTest {
  @MiskTestModule
  val module = MoviesTestModule()

  val maxMaxRows = 40
  val rowCountErrorLimit = 30
  val rowCountWarningLimit = 20
  private val queryFactory = ReflectionQuery.Factory(
    ReflectionQuery.QueryLimitsConfig(
      maxMaxRows, rowCountErrorLimit, rowCountWarningLimit
    )
  )

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var logCollector: LogCollector
  @Inject lateinit var fakeClock: FakeClock

  @Test
  fun comparisonOperators() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))
    val m4 = NameAndReleaseDate("Rocky 4", LocalDate.of(2018, 1, 4))
    val m5 = NameAndReleaseDate("Rocky 5", LocalDate.of(2018, 1, 5))
    val m98 = NameAndReleaseDate("Rocky 98", null)
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))
      session.save(DbMovie(m4.name, m4.releaseDate))
      session.save(DbMovie(m5.name, m5.releaseDate))
      session.save(DbMovie(m98.name, m98.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThan(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m1, m2)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThanOrEqualTo(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m1, m2, m3)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateEqualTo(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactly(m3)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThanOrEqualTo(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m3, m4, m5)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThan(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m4, m5)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateNotEqualTo(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m1, m2, m4, m5)
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

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))
      session.save(DbMovie(m4.name, m4.releaseDate))
      session.save(DbMovie(m5.name, m5.releaseDate))
      session.save(DbMovie(m98.name, m98.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThan(null)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThanOrEqualTo(null)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateEqualTo(null)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThanOrEqualTo(null)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateGreaterThan(null)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateNotEqualTo(null)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()
    }
  }

  @Test
  fun nullOperators() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m98 = NameAndReleaseDate("Rocky 98", null)
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m98.name, m98.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateIsNull()
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m98, m99)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateIsNotNull()
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m1, m2)
    }
  }

  @Test
  fun inOperator() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInVararg(m1.releaseDate, m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m1, m3)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInCollection(listOf(m1.releaseDate, m3.releaseDate))
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m1, m3)
    }
  }

  @Test
  fun notInOperator() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateNotInVararg(m1.releaseDate, m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m2)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateNotInCollection(listOf(m1.releaseDate, m3.releaseDate))
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m2)
    }
  }

  @Test
  fun inOperatorWithNull() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", null)

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInVararg(m1.releaseDate, null)
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactly(m1)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInCollection(listOf(m1.releaseDate, null))
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactly(m1)
    }
  }

  @Test
  fun inOperatorWithEmptyList() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))

    transacter.transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInVararg()
          .allowFullScatter()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateInCollection(listOf())
          .allowFullScatter()
          .listAsNameAndReleaseDate(session)
      )
        .isEmpty()
    }
  }

  @Test
  fun runtimeQueryConstraints() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      val queryObjectResult = queryFactory.newQuery<OperatorsMovieQuery>()
        .releaseDateLessThan(m3.releaseDate)
        .allowFullScatter().allowTableScan()
        .listAsNameAndReleaseDate(session)

      // Should contain the same items as running the query with a runtime constraint.
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { dynamicAddConstraint("release_date", LT, m3.releaseDate) }
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrderElementsOf(queryObjectResult)

      // Should be the same when using a manual projection.
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThan(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .dynamicList(session, listOf("name", "release_date"))
          .map { NameAndReleaseDate(it[0] as String, it[1] as LocalDate) }
      )
        .containsExactlyInAnyOrderElementsOf(queryObjectResult)

      // Should be the same when using a JPA selection.
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThan(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .dynamicList(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.tuple(
              queryRoot.get<DbMovie>("name"),
              queryRoot.get<DbMovie>("release_date")
            )
          }
          .map { NameAndReleaseDate(it[0] as String, it[1] as LocalDate) }
      )
        .containsExactlyInAnyOrderElementsOf(queryObjectResult)

      // Should also be the same as not using the query object at all.
      assertThat(
        queryFactory.dynamicQuery(DbMovie::class)
          .apply { dynamicAddConstraint("release_date", LT, m3.releaseDate) }
          .allowFullScatter().allowTableScan()
          .dynamicList(session, listOf("name", "release_date"))
          .map { NameAndReleaseDate(it[0] as String, it[1] as LocalDate) }
      )
        .containsExactlyInAnyOrderElementsOf(queryObjectResult)
    }
  }

  @Test
  fun jpaQuerySelections() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))
    val m99 = NameAndReleaseDate("Rocky 99", null)

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))
      session.save(DbMovie(m99.name, m99.releaseDate))

      // count(*)
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.count(queryRoot)
          }!![0]
      )
        .isEqualTo(4L)

      // count(name)
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.count(queryRoot.get<DbMovie>("name"))
          }!![0]
      )
        .isEqualTo(4L)

      // count(release_date)
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.count(queryRoot.get<DbMovie>("release_date"))
          }!![0]
      )
        .isEqualTo(3L)

      // min(release_date), max(release_date)
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.tuple(
              criteriaBuilder.min(queryRoot.get("release_date")),
              criteriaBuilder.max(queryRoot.get("release_date"))
            )
          }
      )
        .containsExactly(m1.releaseDate, m3.releaseDate)

      // max(release_date) where release_date < m3.release_date
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThan(m3.releaseDate)
          .allowFullScatter().allowTableScan()
          .dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.max(queryRoot.get("release_date"))
          }
      )
        .containsExactly(m2.releaseDate)
    }
  }

  @Test
  fun singleColumnProjection() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateEqualTo(m1.releaseDate)
          .allowFullScatter().allowTableScan()
          .uniqueName(session)
      )
        .isEqualTo(m1.name)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateLessThanOrEqualTo(m2.releaseDate)
          .allowFullScatter().allowTableScan()
          .listAsNames(session)
      )
        .containsExactlyInAnyOrder(m1.name, m2.name)
    }
  }

  @Test
  fun singleColumnProjectionIsEmpty() {
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .uniqueName(session)
      )
        .isNull()

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .listAsNames(session)
      )
        .isEmpty()
    }
  }

  /**
   * This should be multiple tests but it takes a few seconds to insert many rows so we bundle them
   * all into one big test.
   */
  @Test
  fun rowResultCountWarning() {
    // 18 rows is too small for the warning threshold (20).
    transacter.allowCowrites().transaction { session ->
      for (i in 1..18) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>().allowTableScan().allowFullScatter()
          .list(session)
      ).hasSize(18)
    }
    assertThat(logCollector.takeMessages(loggerClass = ReflectionQuery::class)).isEmpty()

    // 21 rows logs a warning.
    transacter.allowCowrites().transaction { session ->
      for (i in 19..21) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .list(session)
      ).hasSize(21)
    }
    assertThat(
      getOnlyElement(
        logCollector.takeMessages(loggerClass = ReflectionQuery::class, minLevel = Level.WARN)
      )
    ).startsWith("Unbounded query returned 21 rows.")

    // 31 rows logs an error.
    transacter.allowCowrites().transaction { session ->
      for (i in 22..31) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    transacter.allowCowrites().transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .list(session)
      ).hasSize(31)
    }
    assertThat(
      getOnlyElement(
        logCollector.takeMessages(loggerClass = ReflectionQuery::class, minLevel = Level.ERROR)
      )
    ).startsWith("Unbounded query returned 31 rows.")

    // An explicit max row count suppresses the warning.
    transacter.allowCowrites().transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .apply { maxRows = 32 }
          .list(session)
      )
        .hasSize(31)
    }
    assertThat(logCollector.takeMessages(loggerClass = ReflectionQuery::class)).isEmpty()

    // More than 40 rows throws an exception.
    transacter.allowCowrites().transaction { session ->
      for (i in 32..41) {
        session.save(DbMovie("Rocky $i", null))
      }
    }
    assertThat(
      assertFailsWith<IllegalStateException> {
        transacter.transaction { session ->
          queryFactory.newQuery<OperatorsMovieQuery>()
            .allowFullScatter().allowTableScan()
            .list(session)
        }
      }
    ).hasMessage("query truncated at 41 rows")
  }

  @Test
  fun maxRowCountNotExceeded() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .apply { maxRows = 4 }
          .listAsNames(session)
      )
        .hasSize(3)
    }
  }

  @Test
  fun maxMaxRowCountEnforced() {
    val query = queryFactory.newQuery<OperatorsMovieQuery>()
    assertThat(
      assertFailsWith<IllegalArgumentException> {
        query.maxRows = 10_001
      }
    ).hasMessage("out of range: 10001")
  }

  @Test
  fun maxRowCountTruncates() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    // List.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 2 }
          .allowFullScatter().allowTableScan()
          .list(session)
          .map { it.name }
      )
        .hasSize(2)
    }

    // List projection.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .apply { maxRows = 2 }
          .listAsNames(session)
      )
        .hasSize(2)
    }
  }

  @Test
  fun firstResultAndMaxRowCountPicks() {

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Rocky 1", LocalDate.of(2018, 1, 1)))
      session.save(DbMovie("Rocky 2", LocalDate.of(2018, 1, 2)))
      session.save(DbMovie("Rocky 3", LocalDate.of(2018, 1, 3)))
    }

    // List.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 1; firstResult = 1 }
          .allowFullScatter().allowTableScan()
          .releaseDateAsc()
          .list(session)
          .map { it.name }
      )
        .containsExactly("Rocky 2")
    }

    // List projection.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .releaseDateAsc()
          .apply { maxRows = 1; firstResult = 1 }
          .listAsNameAndReleaseDate(session)
          .map { it.name }
      )
        .containsExactly("Rocky 2")
    }

    // Unique.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 1; firstResult = 1 }
          .allowFullScatter().allowTableScan()
          .releaseDateAsc()
          .uniqueResult(session)!!.name
      )
        .isEqualTo("Rocky 2")
    }

    // Unique runtime path.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { maxRows = 1; firstResult = 1 }
          .allowFullScatter().allowTableScan()
          .releaseDateAsc()
          .dynamicUniqueResult(session, listOf("name", "release_date"))!![0]
      )
        .isEqualTo("Rocky 2")
    }
  }

  @Test
  fun firstResultTruncates() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    // List.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { firstResult = 1 }
          .allowFullScatter().allowTableScan()
          .list(session)
          .map { it.name }
      )
        .hasSize(2)
    }

    // List projection.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .apply { firstResult = 1 }
          .listAsNames(session)
      )
        .hasSize(2)
    }
  }

  @Test
  fun largeFirstResultReturnsNothing() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    // List.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { firstResult = 3 }
          .allowFullScatter().allowTableScan()
          .list(session)
          .map { it.name }
      )
        .hasSize(0)
    }

    // List projection.
    transacter.transaction { session ->
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .apply { firstResult = 3 }
          .listAsNames(session)
      )
        .hasSize(0)
    }
  }

  @Test
  fun uniqueResultFailsOnTooManyResults() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Rocky 1", null))
      session.save(DbMovie("Rocky 2", null))
      session.save(DbMovie("Rocky 3", null))
    }

    // Unique result.
    assertThat(
      assertFailsWith<IllegalStateException> {
        transacter.transaction { session ->
          queryFactory.newQuery<OperatorsMovieQuery>()
            .allowFullScatter().allowTableScan()
            .uniqueResult(session)
        }
      }
    ).hasMessageContaining("query expected a unique result but was")

    // Unique result projection.
    assertThat(
      assertFailsWith<IllegalStateException> {
        transacter.transaction { session ->
          queryFactory.newQuery<OperatorsMovieQuery>()
            .allowFullScatter().allowTableScan()
            .uniqueName(session)
        }
      }
    ).hasMessageContaining("query expected a unique result but was")
  }

  @Test
  fun order() {
    transacter.allowCowrites().transaction { session ->
      val jurassicPark = DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
      val rocky = DbMovie("Rocky", LocalDate.of(1976, 11, 21))
      val starWars = DbMovie("Star Wars", LocalDate.of(1977, 5, 25))

      val m1 = NameAndReleaseDate(jurassicPark.name, jurassicPark.release_date)
      val m2 = NameAndReleaseDate(rocky.name, rocky.release_date)
      val m3 = NameAndReleaseDate(starWars.name, starWars.release_date)

      session.save(jurassicPark)
      session.save(rocky)
      session.save(starWars)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateAsc()
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactly(m2, m3, m1)
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateDesc()
          .allowFullScatter().allowTableScan()
          .listAsNameAndReleaseDate(session)
      )
        .containsExactly(m1, m3, m2)
      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .releaseDateDesc()
          .allowFullScatter().allowTableScan()
          .list(session)
      )
        .containsExactly(jurassicPark, starWars, rocky)
    }
  }

  @Test
  fun runtimeOrder() {
    transacter.allowCowrites().transaction { session ->
      val jurassicPark = DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
      val rocky = DbMovie("Rocky", LocalDate.of(1976, 11, 21))
      val starWars = DbMovie("Star Wars", LocalDate.of(1977, 5, 25))

      session.save(jurassicPark)
      fakeClock.add(ofSeconds(1))
      session.save(rocky)
      fakeClock.add(ofSeconds(1))
      session.save(starWars)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { dynamicAddOrder("release_date", false) }
          .allowFullScatter().allowTableScan()
          .list(session)
      )
        .containsExactly(jurassicPark, starWars, rocky)

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .apply { dynamicAddOrder("created_at", true) }
          .allowFullScatter().allowTableScan()
          .list(session)
      )
        .containsExactly(jurassicPark, rocky, starWars)
    }
  }

  @Test
  fun deleteFailsWithOrderBy() {
    transacter.transaction { session ->
      session.save(DbMovie("Rocky 1", null))
    }

    assertThat(
      assertFailsWith<IllegalStateException> {
        transacter.transaction { session ->
          queryFactory.newQuery<OperatorsMovieQuery>()
            .allowFullScatter().allowTableScan()
            .releaseDateAsc().delete(session)
        }
      }
    ).hasMessageContaining("orderBy shouldn't be used for a delete")

    transacter.allowCowrites().transaction { session ->
      val rocky = queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter().allowTableScan()
        .uniqueName(session)
      assertThat(rocky).isEqualTo("Rocky 1")
    }
  }

  @Test
  fun delete() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      session.save(DbMovie("Rocky", LocalDate.of(1976, 11, 21)))
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    transacter.transaction { session ->
      val deleted = queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter()
        .allowTableScan()
        .releaseDateLessThanOrEqualTo(LocalDate.of(1978, 1, 1))
        .delete(session)

      assertThat(deleted).isEqualTo(2)
    }

    transacter.transaction { session ->
      val jurassicPark = queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter()
        .allowTableScan()
        .uniqueName(session)

      assertThat(jurassicPark).isEqualTo("Jurassic Park")
    }
  }

  @Test
  fun count() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      session.save(DbMovie("Rocky", LocalDate.of(1976, 11, 21)))
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    // count all entities
    val allCount = transacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter().allowTableScan()
        .count(session)
    }
    assertThat(allCount).isEqualTo(3)

    // count with filter
    transacter.transaction { session ->
      val jurassicParkCount = queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter().allowTableScan()
        .name("Jurassic Park")
        .count(session)
      assertThat(jurassicParkCount).isEqualTo(1)
    }

    // count beyond max results
    assertFailsWith<java.lang.IllegalStateException> {
      transacter.transaction { session ->
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .apply { maxRows = 2 }
          .count(session)
      }
    }
  }

  @Test
  fun orOperator() {
    val m1 = NameAndReleaseDate("Rocky 1", LocalDate.of(2018, 1, 1))
    val m2 = NameAndReleaseDate("Rocky 2", LocalDate.of(2018, 1, 2))
    val m3 = NameAndReleaseDate("Rocky 3", LocalDate.of(2018, 1, 3))

    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie(m1.name, m1.releaseDate))
      session.save(DbMovie(m2.name, m2.releaseDate))
      session.save(DbMovie(m3.name, m3.releaseDate))

      assertThat(
        queryFactory.newQuery<OperatorsMovieQuery>()
          .allowFullScatter().allowTableScan()
          .or {
            option { name("Rocky 1") }
            option { name("Rocky 3") }
          }
          .listAsNameAndReleaseDate(session)
      )
        .containsExactlyInAnyOrder(m1, m3)
    }
  }

  @Test
  fun orWithZeroOptionsExplodes() {
    transacter.transaction { session ->
      assertFailsWith<IllegalStateException> {
        queryFactory.newQuery<OperatorsMovieQuery>()
          .or {
          }
          .list(session)
      }
    }
  }

  @Test
  fun orWithEmptyOptionExplodes() {
    transacter.transaction { session ->
      assertFailsWith<IllegalStateException> {
        queryFactory.newQuery<OperatorsMovieQuery>()
          .or {
            option { }
          }
          .list(session)
      }
    }
  }

  @Test
  fun orOperatorFailsOnNonPredicateCall() {
    transacter.transaction { session ->
      assertFailsWith<IllegalStateException> {
        queryFactory.newQuery<OperatorsMovieQuery>()
          .or {
            option { list(session) }
          }
          .list(session)
      }
      assertFailsWith<IllegalStateException> {
        queryFactory.newQuery<OperatorsMovieQuery>()
          .or {
            option { releaseDateAsc() }
          }
          .list(session)
      }
    }
  }

  @Test
  fun customConstraint() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      session.save(DbMovie("Jurassic Park: The Lost World", LocalDate.of(1997, 5, 19)))
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    val jurassicCount = transacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter()
        .allowTableScan()
        .constraint { root -> like(root.get("name"), "Jurassic%") }
        .count(session)
    }
    assertThat(jurassicCount).isEqualTo(2)
  }

  @Test
  fun queryCanBeCloned() {
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      session.save(DbMovie("Jurassic Park: The Lost World", LocalDate.of(1997, 5, 19)))
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }

    val original = queryFactory.newQuery<OperatorsMovieQuery>()
      .allowFullScatter()
      .allowTableScan()
      .constraint { root -> like(root.get("name"), "Jurassic%") }

    val clone = original.clone<OperatorsMovieQuery>()

    // Original and cloned queries should provide the same result
    assertThat(
      transacter.transaction { session ->
        original.count(session)
      }
    ).isEqualTo(2)
    assertThat(
      transacter.transaction { session ->
        clone.count(session)
      }
    ).isEqualTo(2)

    // Modify the cloned query. The original query should be unchanged
    clone.constraint { root -> like(root.get("name"), "%World") }
    assertThat(
      transacter.transaction { session ->
        original.count(session)
      }
    ).isEqualTo(2)
    assertThat(
      transacter.transaction { session ->
        clone.count(session)
      }
    ).isEqualTo(1)
  }
}
