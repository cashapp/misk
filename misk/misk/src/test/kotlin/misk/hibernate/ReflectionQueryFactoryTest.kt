package misk.hibernate

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class ReflectionQueryFactoryTest {
  @MiskTestModule
  val module = HibernateTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var clock: Clock

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
      session.save(DbMovie(m1.name, m1.releaseDate, clock.instant()))
      session.save(DbMovie(m2.name, m2.releaseDate, clock.instant()))
      session.save(DbMovie(m3.name, m3.releaseDate, clock.instant()))
      session.save(DbMovie(m4.name, m4.releaseDate, clock.instant()))
      session.save(DbMovie(m5.name, m5.releaseDate, clock.instant()))
      session.save(DbMovie(m98.name, m98.releaseDate, clock.instant()))
      session.save(DbMovie(m99.name, m99.releaseDate, clock.instant()))

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateLessThan(m3.releaseDate)
          .listAs<NameAndReleaseDate>(session))
          .containsExactly(m1, m2)

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateLessThanOrEqualTo(m3.releaseDate)
          .listAs<NameAndReleaseDate>(session))
          .containsExactly(m1, m2, m3)

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateEqualTo(m3.releaseDate)
          .listAs<NameAndReleaseDate>(session))
          .containsExactly(m3)

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateGreaterThanOrEqualTo(m3.releaseDate)
          .listAs<NameAndReleaseDate>(session))
          .containsExactly(m3, m4, m5)

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateGreaterThan(m3.releaseDate)
          .listAs<NameAndReleaseDate>(session))
          .containsExactly(m4, m5)

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateNotEqualTo(m3.releaseDate)
          .listAs<NameAndReleaseDate>(session))
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
      session.save(DbMovie(m1.name, m1.releaseDate, clock.instant()))
      session.save(DbMovie(m2.name, m2.releaseDate, clock.instant()))
      session.save(DbMovie(m3.name, m3.releaseDate, clock.instant()))
      session.save(DbMovie(m4.name, m4.releaseDate, clock.instant()))
      session.save(DbMovie(m5.name, m5.releaseDate, clock.instant()))
      session.save(DbMovie(m98.name, m98.releaseDate, clock.instant()))
      session.save(DbMovie(m99.name, m99.releaseDate, clock.instant()))

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateLessThan(null)
          .listAs<NameAndReleaseDate>(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateLessThanOrEqualTo(null)
          .listAs<NameAndReleaseDate>(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateEqualTo(null)
          .listAs<NameAndReleaseDate>(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateGreaterThanOrEqualTo(null)
          .listAs<NameAndReleaseDate>(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateGreaterThan(null)
          .listAs<NameAndReleaseDate>(session))
          .isEmpty()

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateNotEqualTo(null)
          .listAs<NameAndReleaseDate>(session))
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
      session.save(DbMovie(m1.name, m1.releaseDate, clock.instant()))
      session.save(DbMovie(m2.name, m2.releaseDate, clock.instant()))
      session.save(DbMovie(m98.name, m98.releaseDate, clock.instant()))
      session.save(DbMovie(m99.name, m99.releaseDate, clock.instant()))

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateIsNull()
          .listAs<NameAndReleaseDate>(session))
          .containsExactly(m98, m99)

      assertThat(queryFactory.newQuery<MovieQuery>()
          .releaseDateIsNotNull()
          .listAs<NameAndReleaseDate>(session))
          .containsExactly(m1, m2)
    }
  }

  interface MovieQuery : Query<DbMovie> {
    @Constraint(path = "release_date", operator = Operator.LT)
    fun releaseDateLessThan(upperBound: LocalDate?): MovieQuery

    @Constraint(path = "release_date", operator = Operator.LE)
    fun releaseDateLessThanOrEqualTo(upperBound: LocalDate?): MovieQuery

    @Constraint(path = "release_date", operator = Operator.EQ)
    fun releaseDateEqualTo(upperBound: LocalDate?): MovieQuery

    @Constraint(path = "release_date", operator = Operator.GE)
    fun releaseDateGreaterThanOrEqualTo(upperBound: LocalDate?): MovieQuery

    @Constraint(path = "release_date", operator = Operator.GT)
    fun releaseDateGreaterThan(upperBound: LocalDate?): MovieQuery

    @Constraint(path = "release_date", operator = Operator.NE)
    fun releaseDateNotEqualTo(upperBound: LocalDate?): MovieQuery

    @Constraint(path = "release_date", operator = Operator.IS_NOT_NULL)
    fun releaseDateIsNotNull(): MovieQuery

    @Constraint(path = "release_date", operator = Operator.IS_NULL)
    fun releaseDateIsNull(): MovieQuery
  }

  data class NameAndReleaseDate(
    @Property("name") var name: String,
    @Property("release_date") var releaseDate: LocalDate?
  ) : Projection
}
