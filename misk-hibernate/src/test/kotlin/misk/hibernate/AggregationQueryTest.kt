package misk.hibernate

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.JdbcTestingModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.deployment.TESTING
import java.time.LocalDate

@MiskTest(startService = true)
class AggregationQueryTest {
  @MiskTestModule val module = AggregationQueryTestModule()

  @Inject @PrimitivesDb lateinit var primitiveTransacter: Transacter
  @Inject @Movies lateinit var movieTransacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  private val christmas23 = LocalDate.of(2023, 12, 25)

  private val jurassicPark = DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
  private val rocky = DbMovie("Rocky", LocalDate.of(1976, 11, 21))
  private val starWars = DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
  private val indianaJones = DbMovie("Raiders of the Lost Ark", LocalDate.of(1981, 6, 12))
  private val theColorPurple = DbMovie("The Color Purple", christmas23)
  private val ferrari = DbMovie("Ferrari", christmas23)
  private val theBoysInTheBoat = DbMovie("The Boys in the Boat", christmas23)

  val harrisonFord = DbActor("Harrison Ford")
  val markHamill = DbActor("Mark Hamill")
  val carrieFisher = DbActor("Carrie Fisher")
  val jeffGoldblum = DbActor("Jeff Goldblum")
  val samuelLJackson = DbActor("Samuel L. Jackson")
  val anthonyDaniels = DbActor("Anthony Daniels")

  @BeforeEach fun setup() = seedData()

  @Test fun aggregationInQueries() {
    // Find the average of some numbers (AVG).
    val average = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .averageI64(session)
    }
    assertThat(average).isEqualTo(5.5)

    // Find the count of some numbers (COUNT).
    val count = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .countI64(session)
    }
    assertThat(count).isEqualTo(6L)

    // Find the count of distinct movie titles (COUNT_DISTINCT).
    val uniqueMovieTitles = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .distinctMovieTitles(session)
    }
    assertThat(uniqueMovieTitles).isEqualTo(7)

    // Find the latest movie (MAX).
    val latestMovieReleaseDate = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .allowTableScan()
        .releaseDateMax(session)
    }
    assertThat(latestMovieReleaseDate).isEqualTo(christmas23)

    // Find the oldest movie (MIN).
    val oldestMovieReleaseDate = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .releaseDateMin(session)
    }
    assertThat(oldestMovieReleaseDate).isEqualTo(rocky.release_date!!)

    // Find the sum of some numbers (SUM).
    val sum = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .sumI64(session)
    }
    assertThat(sum).isEqualTo(33)
  }

  @Test fun aggregationInProjections() {
    // Find the average of some numbers (AVG).
    val average = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .averageAll(session)
    }
    // Yes, the average of all the numbers is really 5.5; 6 rows which all sum to 33.
    assertThat(average)
      .isEqualTo(AveragePrimitiveTour(5.5, 5.5, 5.5, 5.5, 5.5, 5.5))

    // Find the count of some numbers (COUNT).
    val count = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .countAll(session)
    }
    assertThat(count)
      .isEqualTo(CountPrimitiveTour(6, 6, 6, 6, 6, 6, 6, 6))

    // Find the count of distinct numbers (COUNT_DISTINCT).
    val countDistinct = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .countDistinctAll(session)
    }
    assertThat(countDistinct)
      .isEqualTo(CountDistinctPrimitiveTour(2, 6, 2, 2, 2, 2, 2, 2))

    // Find the latest movie (MAX).
    val latestMovie = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .latestReleasedMovie(session)
    }
    assertThat(latestMovie).isEqualTo(LatestReleaseDate(christmas23))

    // Find the oldest movie (MIN).
    val oldestMovie = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .oldestReleasedMovie(session)
    }
    assertThat(oldestMovie).isEqualTo(OldestReleaseDate(rocky.release_date!!))

    // Find the sum of some numbers (SUM).
    val sum = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .sumAll(session)
    }
    // Once again, yes, the sum of all the numbers is really 33.
    assertThat(sum).isEqualTo(SumPrimitiveTour(33, 33, 33, 33, 33.0, 33.0))
  }

  @Test fun `aggregations in projections work with groups`() {
    // Simple count in one table.
    val releaseCountByDate = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .allowTableScan()
        .groupByReleaseDate()
        .datesWithReleaseCount(session)
    }

    assertThat(releaseCountByDate).containsExactlyInAnyOrder(
      DateWithReleaseCount(jurassicPark.release_date!!, 1),
      DateWithReleaseCount(rocky.release_date!!, 1),
      DateWithReleaseCount(starWars.release_date!!, 1),
      DateWithReleaseCount(indianaJones.release_date!!, 1),
      DateWithReleaseCount(christmas23, 3)
    )

    // Simple max in one table.
    val numbersFromMaxi8 = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .groupByI1AndC16()
        .listI1C16AndMaxI8(session)
    }
    assertThat(numbersFromMaxi8).containsExactlyInAnyOrder(
      I1C16AndMaxI8(true, '6', 2),
      I1C16AndMaxI8(false, '5', 11),
    )

    // Count with a join.
    val charactersPerActor = movieTransacter.transaction { session ->
      queryFactory.newQuery<CharacterQuery>()
        .allowTableScan()
        .withActorForProjection()
        .groupByActorName()
        .listAsActorAndCharacterCount(session)
    }
    assertThat(charactersPerActor).containsExactlyInAnyOrder(
      ActorAndCharacterCount(harrisonFord.name, 2),
      ActorAndCharacterCount(markHamill.name, 1),
      ActorAndCharacterCount(carrieFisher.name, 1),
      ActorAndCharacterCount(jeffGoldblum.name, 1),
      ActorAndCharacterCount(samuelLJackson.name, 1),
      ActorAndCharacterCount(anthonyDaniels.name, 1),
    )
  }

  @Test fun `finds errors in query interfaces`() {
    val ex = assertThrows<RuntimeException> {
      primitiveTransacter.transaction { session ->
        queryFactory.newQuery<WrongTypesPrimitiveTourQuery>()
          .averageI64(session)
      }
    }

    assertThat(ex).hasMessage(
      """
      |java.lang.IllegalArgumentException: Query class misk.hibernate.WrongTypesPrimitiveTourQuery has problems:
      |  averageI64() return element type must be Double? for AVG aggregations, but was java.lang.Long
      |  countDistinctI64() return element type must be Long? for COUNT_DISTINCT aggregations, but was java.lang.String
      |  countI64() return element type must be Long? for COUNT aggregations, but was java.lang.Double
      |  maxI64() return element type must be out Comparable? for MAX aggregations, but was java.lang.Object
      |  minI64() return element type must be out Comparable? for MIN aggregations, but was java.lang.Object
      |  sumI64() return element type must be out Number? for SUM aggregations, but was java.time.LocalDate""".trimMargin()
    )
  }

  @Test fun `finds errors in projections with aggregated properties`() {
    val ex = assertThrows<RuntimeException> {
      primitiveTransacter.transaction { session ->
        queryFactory.newQuery<WrongAggregatedProjectionQuery>()
          .wrongAggregation(session)
      }
    }

    assertThat(ex).hasMessage(
      """
      |java.lang.IllegalArgumentException: Query class misk.hibernate.WrongAggregatedProjectionQuery has problems:
      |  misk.hibernate.WrongAggregatedProjection#average element type must be Double? for AVG aggregations, but was java.lang.Object
      |  misk.hibernate.WrongAggregatedProjection#count element type must be Long? for COUNT aggregations, but was java.lang.Object
      |  misk.hibernate.WrongAggregatedProjection#countDistinct element type must be Long? for COUNT_DISTINCT aggregations, but was java.lang.Object
      |  misk.hibernate.WrongAggregatedProjection#max element type must be out Comparable? for MAX aggregations, but was java.lang.Object
      |  misk.hibernate.WrongAggregatedProjection#min element type must be out Comparable? for MIN aggregations, but was java.lang.Object
      |  misk.hibernate.WrongAggregatedProjection#sum element type must be out Number? for SUM aggregations, but was java.lang.Object""".trimMargin()
    )
  }

  @Test fun `aggregations work when there are no matching rows`() {
    primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .delete(session)
    }
    primitiveTransacter.transaction { session ->
      // If the query returns no rows, the result should be null.
      queryFactory.newQuery<PrimitiveTourQuery>()
        .averageI64(session)
        .let { assertThat(it).isNull() }

      // If the query returns all null, and the projection cannot accept all nulls, it should
      // return an empty list.
      queryFactory.newQuery<PrimitiveTourQuery>()
        .groupByI1AndC16()
        .listI1C16AndMaxI8(session)
        .let { assertThat(it).isEmpty()}

      // If the query returns all nulls and the projection can accept all nulls, it should return
      // an instance of the projection.
      queryFactory.newQuery<NoDataPrimitivesQuery>()
        .listAllNullsAreOk(session)
        .let { assertThat(it).containsExactly(AllNullsAreOk(null, null)) }

      queryFactory.newQuery<NoDataPrimitivesQuery>()
        .allNullsAreOk(session)
        .let { assertThat(it).isEqualTo(AllNullsAreOk(null, null)) }
    }
  }

  private fun seedData() {
    movieTransacter.transaction { session ->
      val dbRocky = session.save(rocky).let { session.load(it) }
      val dbJurassicPark = session.save(jurassicPark).let { session.load(it) }
      val dbStarWars = session.save(starWars).let { session.load(it) }
      val dbIndianaJones = session.save(indianaJones).let { session.load(it) }
      session.save(theColorPurple)
      session.save(ferrari)
      session.save(theBoysInTheBoat)

      val dbJeffGoldbum = session.save(jeffGoldblum).let { session.load(it) }
      val dbSamuelLJackson = session.save(samuelLJackson).let { session.load(it) }
      val dbHarrisonFord = session.save(harrisonFord).let { session.load(it) }
      val dbMarkHamill = session.save(markHamill).let { session.load(it) }
      val dbCarrieFisher = session.save(carrieFisher).let { session.load(it) }
      val dbAnthonyDaniels = session.save(anthonyDaniels).let { session.load(it) }

      session.save(DbCharacter("Dr. Ian Malcolm", dbJurassicPark, dbJeffGoldbum))
      session.save(DbCharacter("Ray Arnold", dbJurassicPark, dbSamuelLJackson))
      session.save(DbCharacter("Han Solo", dbStarWars, dbHarrisonFord))
      session.save(DbCharacter("Luke Skywalker", dbStarWars, dbMarkHamill))
      session.save(DbCharacter("Princess Leia", dbStarWars, dbCarrieFisher))
      session.save(DbCharacter("C-3P0", dbStarWars, dbAnthonyDaniels))
      session.save(DbCharacter("Indiana Jones", dbIndianaJones, dbHarrisonFord))
    }
    primitiveTransacter.transaction { session ->
      session.save(DbPrimitiveTour(false, 9, 8, 7, 6, '5', 4.0f, 3.0))
      session.save(DbPrimitiveTour(false, 10, 8, 7, 6, '5', 4.0f, 3.0))
      session.save(DbPrimitiveTour(false, 11, 8, 7, 6, '5', 4.0f, 3.0))
      session.save(DbPrimitiveTour(true, 0, 3, 4, 5, '6', 7.0f, 8.0))
      session.save(DbPrimitiveTour(true, 1, 3, 4, 5, '6', 7.0f, 8.0))
      session.save(DbPrimitiveTour(true, 2, 3, 4, 5, '6', 7.0f, 8.0))
    }
  }
}

class AggregationQueryTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DeploymentModule(TESTING))

    val primitivesConfig = MiskConfig.load<RootConfig>("primitivecolumns", TESTING)
    install(JdbcTestingModule(PrimitivesDb::class))
    install(HibernateModule(PrimitivesDb::class, primitivesConfig.data_source))
    install(object : HibernateEntityModule(PrimitivesDb::class) {
      override fun configureHibernate() {
        addEntities(DbPrimitiveTour::class)
      }
    })

    val moviesConfig = MiskConfig.load<MoviesConfig>("moviestestmodule", TESTING)
    val dataSourceConfig = moviesConfig.mysql_data_source
    install(JdbcTestingModule(Movies::class, scaleSafetyChecks = false))
    install(
      HibernateModule(
        Movies::class, MoviesReader::class,
        DataSourceClusterConfig(writer = dataSourceConfig, reader = dataSourceConfig)
      )
    )
    install(object : HibernateEntityModule(Movies::class) {
      override fun configureHibernate() {
        addEntities(DbMovie::class, DbActor::class, DbCharacter::class)
      }
    })
  }
}

private interface WrongTypesPrimitiveTourQuery : Query<DbPrimitiveTour> {
  @Select(path = "i64", aggregation = AggregationType.AVG)
  fun averageI64(session: Session): Long?

  @Select(path = "i64", aggregation = AggregationType.COUNT)
  fun countI64(session: Session): Double?

  @Select(path = "i64", aggregation = AggregationType.COUNT_DISTINCT)
  fun countDistinctI64(session: Session): String?

  @Select(path = "i64", aggregation = AggregationType.MAX)
  fun maxI64(session: Session): Any?

  @Select(path = "i64", aggregation = AggregationType.MIN)
  fun minI64(session: Session): Any?

  @Select(path = "i64", aggregation = AggregationType.SUM)
  fun sumI64(session: Session): LocalDate?
}


interface WrongAggregatedProjectionQuery : Query<DbPrimitiveTour> {
  @Select
  fun wrongAggregation(session: Session): WrongAggregatedProjection?
}

interface NoDataPrimitivesQuery: Query<DbPrimitiveTour> {
  @Select
  fun listAllNullsAreOk(session: Session): List<AllNullsAreOk>

  @Select
  fun allNullsAreOk(session: Session): AllNullsAreOk?
}

data class AllNullsAreOk(
  @Property(path = "i1", aggregation = AggregationType.MAX) val maxi1: Long?,
  @Property(path = "i8", aggregation = AggregationType.MAX) val maxi8: Long?
): Projection

data class WrongAggregatedProjection(
  @Property(path = "i64", aggregation = AggregationType.AVG) val average: Any,
  @Property(path = "i64", aggregation = AggregationType.COUNT) val count: Any,
  @Property(path = "i64", aggregation = AggregationType.COUNT_DISTINCT) val countDistinct: Any,
  @Property(path = "i64", aggregation = AggregationType.MAX) val max: Any,
  @Property(path = "i64", aggregation = AggregationType.MIN) val min: Any,
  @Property(path = "i64", aggregation = AggregationType.SUM) val sum: Any,
) : Projection
