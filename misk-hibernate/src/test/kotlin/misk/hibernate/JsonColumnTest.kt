package misk.hibernate

import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
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
class JsonColumnTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @WillFerrellDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun happyPath() {
    transacter.transaction { session ->
      session.save(DbWillFerrellMovie("Anchorman", listOf("Vince Vaughn", "Christina Applegate"),
          Setting("San Diego", "1970")))
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
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<RootConfig>("jsoncolumn", Environment.TESTING)
      install(HibernateTestingModule(WillFerrellDb::class, config.data_source))
      install(HibernateModule(WillFerrellDb::class, config.data_source))
      install(object : HibernateEntityModule(WillFerrellDb::class) {
        override fun configureHibernate() {
          addEntities(DbWillFerrellMovie::class)
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

  data class Setting(val place: String, val year: String)

  data class NameAndCameos(
    @Property("name") val name: String,
    @Property("cameos") val cameos: List<String>,
    @Property("setting") val setting: Setting?
  ) : Projection

  interface WillFerrellMovieQuery : Query<DbWillFerrellMovie> {
    @Constraint(path = "name")
    fun name(name: String): WillFerrellMovieQuery

    @Select
    fun nameAndCameosAndSetting(session: Session): List<NameAndCameos>
  }
}
