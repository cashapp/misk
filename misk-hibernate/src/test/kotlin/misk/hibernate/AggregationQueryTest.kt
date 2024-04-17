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
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import java.time.LocalDate

@MiskTest(startService = true)
class AggregationQueryTest {
  @MiskTestModule val module = AggregationQueryTestModule()

  @Inject @PrimitivesDb lateinit var primitiveTransacter: Transacter
  @Inject @Movies lateinit var movieTransacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  private val jurassicPark = DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
  private val rocky = DbMovie("Rocky", LocalDate.of(1976, 11, 21))
  private val starWars = DbMovie("Star Wars", LocalDate.of(1977, 5, 25))

  @Test fun aggregationInQueries() {
    seedData()

    // Find the average of some numbers (AVG).

    // Find the count of movies (COUNT).

    // Find the count of distinct movie titles (COUNT_DISTINCT).

    // Find the latest movie (MAX).
    val latestMovieReleaseDate = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .allowFullScatter().allowTableScan()
        .releaseDateMax(session)
    }
    Assertions.assertThat(latestMovieReleaseDate).isEqualTo(LocalDate.of(1993, 6, 9))

    // Find the oldest movie (MIN).

    // Find the sum of all movie ids (SUM). [Useless query but demonstrates the concept.]

  }

  @Test fun aggregationInProjections() {
    seedData()

    // Find the average movie id (AVG). [Useless query but demonstrates the concept.]

    // Find the count of movies (COUNT).

    // Find the count of distinct movie titles (COUNT_DISTINCT).

    // Find the latest movie (MAX).
    val latestMovie = movieTransacter.transaction { session ->
      queryFactory.newQuery<OperatorsMovieQuery>()
        .latestReleasedMovie(session)
    }
    Assertions.assertThat(latestMovie).isEqualTo(LatestReleasedMovie(jurassicPark.name, jurassicPark.release_date!!))

    // Find the oldest movie (MIN).

    // Find the sum of all movie ids (SUM). [Useless query but demonstrates the concept.]
  }

  private fun seedData() {
    movieTransacter.transaction { session ->
      session.save(jurassicPark)
      session.save(rocky)
      session.save(starWars)
    }
    primitiveTransacter.transaction { session ->
      session.save(DbPrimitiveTour(false, 9, 8, 7, 6, '5', 4.0f, 3.0))
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
    val dataSourceConfig = moviesConfig.vitess_mysql_data_source
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

