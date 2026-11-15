package misk.hibernate

import java.time.LocalDate

interface OperatorsMovieQuery : Query<DbMovie> {
  @Constraint(path = "name") fun name(name: String): OperatorsMovieQuery

  @Constraint(path = "name", operator = Operator.LIKE) fun nameLike(pattern: String): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.LT)
  fun releaseDateLessThan(upperBound: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.LE)
  fun releaseDateLessThanOrEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.EQ)
  fun releaseDateEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.EQ_OR_IS_NULL)
  fun releaseDateEqualToOrNull(upperBound: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.GE)
  fun releaseDateGreaterThanOrEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.GT)
  fun releaseDateGreaterThan(upperBound: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.NE)
  fun releaseDateNotEqualTo(upperBound: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.IN)
  fun releaseDateInVararg(vararg upperBounds: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.NOT_IN)
  fun releaseDateNotInVararg(vararg upperBounds: LocalDate?): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.IN)
  fun releaseDateInCollection(upperBounds: Collection<LocalDate?>): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.NOT_IN)
  fun releaseDateNotInCollection(upperBounds: Collection<LocalDate?>): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.IS_NOT_NULL) fun releaseDateIsNotNull(): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.IS_NULL) fun releaseDateIsNull(): OperatorsMovieQuery

  @Order(path = "release_date") fun releaseDateAsc(): OperatorsMovieQuery

  @Order(path = "release_date", asc = false) fun releaseDateDesc(): OperatorsMovieQuery

  @Select fun listAsNameAndReleaseDate(session: Session): List<NameAndReleaseDate>

  @Select("name") fun uniqueName(session: Session): String?

  @Select("name") fun listAsNames(session: Session): List<String>

  @Select(path = "release_date", aggregation = AggregationType.MAX) fun releaseDateMax(session: Session): LocalDate?

  @Select(path = "release_date", aggregation = AggregationType.MIN) fun releaseDateMin(session: Session): LocalDate?

  @Select(path = "name", aggregation = AggregationType.COUNT_DISTINCT) fun distinctMovieTitles(session: Session): Long?

  @Select fun latestReleasedMovie(session: Session): LatestReleaseDate?

  @Select fun oldestReleasedMovie(session: Session): OldestReleaseDate?

  @Group(paths = ["release_date"]) fun groupByReleaseDate(): OperatorsMovieQuery

  @Select fun datesWithReleaseCount(session: Session): List<DateWithReleaseCount>
}

data class LatestReleaseDate(
  @Property(path = "release_date", aggregation = AggregationType.MAX) val release_date: LocalDate
) : Projection

data class OldestReleaseDate(
  @Property(path = "release_date", aggregation = AggregationType.MIN) val release_date: LocalDate
) : Projection

data class DateWithReleaseCount(
  @Property(path = "release_date") val release_date: LocalDate,
  @Property(path = "name", aggregation = AggregationType.COUNT) val count: Long?,
) : Projection
