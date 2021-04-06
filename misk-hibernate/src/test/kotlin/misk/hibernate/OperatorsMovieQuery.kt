package misk.hibernate

import java.time.LocalDate

interface OperatorsMovieQuery : Query<DbMovie> {
  @Constraint(path = "name")
  fun name(name: String): OperatorsMovieQuery

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

  @Constraint(path = "release_date", operator = Operator.IS_NOT_NULL)
  fun releaseDateIsNotNull(): OperatorsMovieQuery

  @Constraint(path = "release_date", operator = Operator.IS_NULL)
  fun releaseDateIsNull(): OperatorsMovieQuery

  @Order(path = "release_date")
  fun releaseDateAsc(): OperatorsMovieQuery

  @Order(path = "release_date", asc = false)
  fun releaseDateDesc(): OperatorsMovieQuery

  @Select
  fun listAsNameAndReleaseDate(session: Session): List<NameAndReleaseDate>

  @Select("name")
  fun uniqueName(session: Session): String?

  @Select("name")
  fun listAsNames(session: Session): List<String>
}
