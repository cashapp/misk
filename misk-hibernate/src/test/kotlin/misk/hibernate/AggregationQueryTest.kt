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
  private val theColorPurple = DbMovie("The Color Purple", christmas23)
  private val ferrari = DbMovie("Ferrari", christmas23)
  private val theBoysInTheBoat = DbMovie("The Boys in the Boat", christmas23)

  @BeforeEach fun setup() = seedData()

  @Test fun aggregationInQueries() {
    // Find the average of some numbers (AVG).
    val average = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .averageI64(session)
    }
    assertThat(average).isEqualTo(5.25)

    // Find the count of some numbers (COUNT).
    val count = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .countI64(session)
    }
    assertThat(count).isEqualTo(4L)

    // Find the count of distinct movie titles (COUNT_DISTINCT).
    val uniqueMovieTitles = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .distinctMovieTitles(session)
    }
    assertThat(uniqueMovieTitles).isEqualTo(6)

    // Find the latest movie (MAX).
    val latestMovieReleaseDate = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter().allowTableScan()
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
    assertThat(sum).isEqualTo(21)
  }

  @Test fun aggregationInProjections() {
    // Find the average of some numbers (AVG).
    val average = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .averageAll(session)
    }
    assertThat(average)
      .isEqualTo(AveragePrimitiveTour(3.75, 4.25, 4.75, 5.25, 6.25, 6.75))

    // Find the count of some numbers (COUNT).
    val count = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .countAll(session)
    }
    assertThat(count)
      .isEqualTo(CountPrimitiveTour(4, 4, 4, 4, 4, 4, 4, 4))

    // Find the count of distinct numbers (COUNT_DISTINCT).
    val countDistinct = primitiveTransacter.transaction { session ->
      queryFactory.newQuery<PrimitiveTourQuery>()
        .countDistinctAll(session)
    }
    assertThat(countDistinct)
      .isEqualTo(CountDistinctPrimitiveTour(2, 2, 2, 2, 2, 2, 2, 2))

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
    assertThat(sum).isEqualTo(SumPrimitiveTour(15, 17, 19, 21, 25.0, 27.0))
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

  private fun seedData() {
    movieTransacter.transaction { session ->
      session.save(jurassicPark)
      session.save(rocky)
      session.save(starWars)
      session.save(theColorPurple)
      session.save(ferrari)
      session.save(theBoysInTheBoat)
    }
    primitiveTransacter.transaction { session ->
      session.save(DbPrimitiveTour(false, 9, 8, 7, 6, '5', 4.0f, 3.0))
      session.save(DbPrimitiveTour(true, 2, 3, 4, 5, '6', 7.0f, 8.0))
      session.save(DbPrimitiveTour(true, 2, 3, 4, 5, '6', 7.0f, 8.0))
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
