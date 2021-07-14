package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import kotlin.test.assertNull

@MiskTest(startService = true)
class JsonColumnTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @WillFerrellDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun happyPath() {
    transacter.transaction { session ->
      session.save(
        DbWillFerrellMovie(
          "Anchorman", listOf("Vince Vaughn", "Christina Applegate"),
          Setting("San Diego", "1970")
        )
      )
    }
    transacter.transaction { session ->
      val movie = queryFactory.newQuery(WillFerrellMovieQuery::class)
        .allowTableScan()
        .name("Anchorman")
        .nameAndCameosAndSetting(session)[0]
      assertThat(movie.name).isEqualTo("Anchorman")
      assertThat(movie.cameos).isEqualTo(listOf("Vince Vaughn", "Christina Applegate"))
      assertThat(movie.setting).isEqualTo(Setting("San Diego", "1970"))
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))

      val config = MiskConfig.load<RootConfig>("jsoncolumn", TESTING)
      install(HibernateTestingModule(WillFerrellDb::class, config.data_source))
      install(HibernateModule(WillFerrellDb::class, config.data_source))
      install(object : HibernateEntityModule(WillFerrellDb::class) {
        override fun configureHibernate() {
          addEntities(DbWillFerrellMovie::class)
          addEntities(DbWillFerrellMovie2::class)
        }
      })
    }
  }

  @Test
  fun nullColumnTest() {
    transacter.transaction { session ->
      session.save(DbWillFerrellMovie("Anchorman", listOf("Vince Vaughn", "Christina Applegate")))
    }
    transacter.transaction { session ->
      val movie = queryFactory.newQuery(WillFerrellMovieQuery::class)
        .allowTableScan()
        .name("Anchorman")
        .nameAndCameosAndSetting(session)[0]
      assertThat(movie.name).isEqualTo("Anchorman")
      assertThat(movie.cameos).isEqualTo(listOf("Vince Vaughn", "Christina Applegate"))
      assertNull(movie.setting)
    }
  }

  @Test
  fun nullFieldTest() {
    transacter.transaction { session ->
      session.save(
        DbWillFerrellMovie(
          "Anchorman",
          listOf("Vince Vaughn", "Christina Applegate"),
          Setting("San Diego", "1970")
        )
      )
    }
    transacter.transaction { session ->
      val movie = queryFactory.newQuery(WillFerrellMovie2Query::class)
        .allowTableScan()
        .name("Anchorman")
        .nameAndCameosAndSetting(session)[0]
      assertThat(movie.name).isEqualTo("Anchorman")
      assertThat(movie.cameos).isEqualTo(listOf("Vince Vaughn", "Christina Applegate"))
      assertThat(movie.setting).isEqualTo(Setting2("San Diego"))

    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class WillFerrellDb

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Entity
  @Table(name = "will_ferrell_movies")
  class DbWillFerrellMovie : DbUnsharded<DbWillFerrellMovie> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbWillFerrellMovie>

    @Column(nullable = false)
    var name: String

    @Column(nullable = false)
    @JsonColumn
    var cameos: List<String>

    @Column
    @JsonColumn
    var setting: Setting?

    constructor(name: String, cameos: List<String>, setting: Setting? = null) {
      this.name = name
      this.cameos = cameos
      this.setting = setting
    }
  }

  @Entity
  @Table(name = "will_ferrell_movies")
  class DbWillFerrellMovie2 : DbUnsharded<DbWillFerrellMovie2> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbWillFerrellMovie2>

    @Column(nullable = false)
    var name: String

    @Column(nullable = false)
    @JsonColumn
    var cameos: List<String>

    @Column
    @JsonColumn
    var setting: Setting2?

    constructor(name: String, cameos: List<String>, setting: Setting2? = null) {
      this.name = name
      this.cameos = cameos
      this.setting = setting
    }
  }

  data class Setting(val place: String, val year: String)
  data class Setting2(val place: String)

  data class NameAndCameos(
    @Property("name") val name: String,
    @Property("cameos") val cameos: List<String>,
    @Property("setting") val setting: Setting?
  ) : Projection

  data class NameAndCameos2(
    @Property("name") val name: String,
    @Property("cameos") val cameos: List<String>,
    @Property("setting") val setting: Setting2?
  ) : Projection

  interface WillFerrellMovieQuery : Query<DbWillFerrellMovie> {
    @Constraint(path = "name")
    fun name(name: String): WillFerrellMovieQuery

    @Select
    fun nameAndCameosAndSetting(session: Session): List<NameAndCameos>
  }

  interface WillFerrellMovie2Query : Query<DbWillFerrellMovie2> {
    @Constraint(path = "name")
    fun name(name: String): WillFerrellMovie2Query

    @Select
    fun nameAndCameosAndSetting(session: Session): List<NameAndCameos2>
  }
}
