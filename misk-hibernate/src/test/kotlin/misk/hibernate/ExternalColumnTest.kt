package misk.hibernate

import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.JoinColumn
import javax.persistence.OneToOne
import javax.persistence.Table
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

/**
 * Confirm we do the right thing when an entity is linked to another entity via a column in that
 * other entity's table.
 */
@MiskTest(startService = true)
class ExternalColumnTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @ExternalColumnDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun `traverse relationships`() {
    transacter.transaction { session ->
      val mustang = DbCar("mustang")
      val mustangId = session.save(mustang)
      session.save(DbDriver("jesse", mustang, mustangId))
    }
    transacter.transaction { session ->
      val onlyCar = queryFactory.dynamicQuery(DbCar::class)
          .allowTableScan()
          .uniqueResult(session)
      assertThat(onlyCar?.driver?.name).isEqualTo("jesse")
      val onlyDriver = queryFactory.dynamicQuery(DbDriver::class)
          .allowTableScan()
          .uniqueResult(session)
      assertThat(onlyDriver?.car?.model).isEqualTo("mustang")
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<RootConfig>("externalcolumn", Environment.TESTING)
      install(HibernateTestingModule(ExternalColumnDb::class, config.data_source))
      install(HibernateModule(ExternalColumnDb::class, config.data_source))
      install(object : HibernateEntityModule(ExternalColumnDb::class) {
        override fun configureHibernate() {
          addEntities(DbCar::class)
          addEntities(DbDriver::class)
        }
      })
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class ExternalColumnDb

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Entity
  @Table(name = "cars")
  class DbCar(
    @Column(nullable = false)
    var model: String? = null,

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "car")
    var driver: DbDriver? = null,
  ) : DbUnsharded<DbCar> {
    @javax.persistence.Id @GeneratedValue override lateinit var id: Id<DbCar>
  }

  @Entity
  @Table(name = "drivers")
  class DbDriver(
    @Column(nullable = false)
    var name: String? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", updatable = false, insertable = false)
    var car: DbCar,

    @Column(updatable = false)
    var car_id: Id<DbCar>
  ) : DbUnsharded<DbDriver> {
    @javax.persistence.Id @GeneratedValue override lateinit var id: Id<DbDriver>
  }
}
