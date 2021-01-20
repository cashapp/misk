package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.SuperHero.*
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import kotlin.test.assertNull

@MiskTest
class ProtoColumnTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @SuperHeroMoviesDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun basicPath() {
    transacter.transaction { session ->
      session.save(DbAvengersMovie(
          name = "CivilWar",
          hero = SuperHero.Builder()
              .civilian_name("Tony Stark")
              .super_hero_name("IronMan")
              .powers(listOf(SuperPower("suit", 10), SuperPower("humor", 2)))
              .build(),
          anotherhero = SuperHero.Builder()
              .civilian_name("Steven")
              .super_hero_name("Captain America")
              .powers(listOf(SuperPower("Shield", 11)))
              .build())
      )
    }
    transacter.transaction { session ->
      val movie = queryFactory.newQuery(DbAvengersMovieQuery::class)
          .allowTableScan()
          .name("CivilWar")
          .nameAndHero(session)[0]
      assertThat(movie.name).isEqualTo("CivilWar")
      assertThat(movie.hero).isEqualTo(SuperHero.Builder()
          .civilian_name("Tony Stark")
          .super_hero_name("IronMan")
          .powers(listOf(SuperPower("suit", 10), SuperPower("humor", 2)))
          .build())
      assertThat(movie.anotherhero).isEqualTo(SuperHero.Builder()
          .civilian_name("Steven")
          .super_hero_name("Captain America")
          .powers(listOf(SuperPower("Shield", 11)))
          .build())
    }
  }

  @Test
  fun nullColumnTest() {
    transacter.transaction { session ->
      session.save(DbAvengersMovie("IronMan", SuperHero.Builder()
          .civilian_name("Tony Stark")
          .super_hero_name("IronMan")
          .powers(listOf(SuperPower("suit", 10), SuperPower("humor", 2)))
          .build()))
    }
    transacter.transaction { session ->
      val movie = queryFactory.newQuery(DbAvengersMovieQuery::class)
        .allowTableScan()
        .name("IronMan")
        .nameAndHero(session)[0]
      assertThat(movie.name).isEqualTo("IronMan")
      assertThat(movie.hero).isEqualTo(SuperHero.Builder()
          .civilian_name("Tony Stark")
          .super_hero_name("IronMan")
          .powers(listOf(SuperPower("suit", 10), SuperPower("humor", 2)))
          .build())
      assertNull(movie.anotherhero)
    }
  }
  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<RootConfig>("protocolumn", Environment.TESTING)
      install(HibernateTestingModule(SuperHeroMoviesDb::class, config.data_source))
      install(HibernateModule(SuperHeroMoviesDb::class, config.data_source))
      install(object : HibernateEntityModule(SuperHeroMoviesDb::class) {
        override fun configureHibernate() {
          addEntities(DbAvengersMovie::class)
        }
      })
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class SuperHeroMoviesDb

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Entity
  @Table(name = "avengers_movies")
  class DbAvengersMovie : DbUnsharded<DbAvengersMovie> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbAvengersMovie>

    @Column(nullable = false)
    var name: String

    @Column(nullable = false)
    @ProtoColumn
    var hero: SuperHero

    @Column
    @ProtoColumn
    var anotherhero: SuperHero? = null

    constructor(name: String, hero: SuperHero, anotherhero: SuperHero? = null) {
      this.name = name
      this.hero = hero
      this.anotherhero = anotherhero
      hero.encode()
    }
  }

  data class NameAndHero(
    @Property("name") val name: String,
    @Property("hero") val hero: SuperHero,
    @Property("anotherhero") val anotherhero: SuperHero?
  ) : Projection

  interface DbAvengersMovieQuery : Query<DbAvengersMovie> {
    @Constraint(path = "name")
    fun name(name: String): DbAvengersMovieQuery

    @Select
    fun nameAndHero(session: Session): List<NameAndHero>
  }
}
