package misk.hibernate

import jakarta.inject.Inject
import jakarta.inject.Qualifier
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Table

abstract class IdTypeTest(
  dataSourceType: DataSourceType,
) {
  @MiskTestModule
  val module = TestModule(dataSourceType)

  @Inject @IdTypeDb private lateinit var transacter: Transacter

  @Test
  fun `can store and reload entities with id column declared first in table`() {
    val id = transacter.transaction { session: Session ->
      session.save(DbFirst("this is a test"))
    }

    transacter.failSafeRead { session: Session ->
      assertThat(session.load(id).text).isEqualTo("this is a test")
    }
  }

  @Test
  fun `can store and reload entities with id column declared last in table`() {
    val id = transacter.transaction { session: Session ->
      session.save(DbLast("this is another test"))
    }

    transacter.failSafeRead { session: Session ->
      assertThat(session.load(id).text).isEqualTo("this is another test")
    }
  }

  class TestModule(private val dataSourceType: DataSourceType) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))

      val config = MiskConfig.load<RootConfig>("idtype", TESTING)
      install(HibernateModule(IdTypeDb::class, selectDataSourceConfig(config)))
      install(object : HibernateEntityModule(IdTypeDb::class) {
        override fun configureHibernate() {
          addEntities(DbFirst::class)
          addEntities(DbLast::class)
        }
      })
    }

    private fun selectDataSourceConfig(config: RootConfig): DataSourceConfig {
      return when (dataSourceType) {
        DataSourceType.VITESS_MYSQL -> config.vitess_mysql_data_source
        DataSourceType.MYSQL -> config.mysql_data_source
        DataSourceType.COCKROACHDB -> config.cockroachdb_data_source
        DataSourceType.POSTGRESQL -> config.postgresql_data_source
        DataSourceType.TIDB -> config.tidb_data_source
        DataSourceType.HSQLDB -> throw RuntimeException("Not supported (yet?)")
      }
    }
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class IdTypeDb

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
    val postgresql_data_source: DataSourceConfig,
    val cockroachdb_data_source: DataSourceConfig,
    val vitess_mysql_data_source: DataSourceConfig,
    val tidb_data_source: DataSourceConfig,
  ) : Config

  @Entity
  @Table(name = "with_first_column_id")
  class DbFirst() : DbUnsharded<DbFirst> {
    @javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override lateinit var id: Id<DbFirst>

    @Column(nullable = false)
    lateinit var text: String

    constructor(text: String) : this() {
      this.text = text
    }
  }

  @Entity
  @Table(name = "with_last_column_id")
  class DbLast() : DbUnsharded<DbLast> {
    @javax.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override lateinit var id: Id<DbLast>

    @Column(nullable = false)
    lateinit var text: String

    constructor(text: String) : this() {
      this.text = text
    }
  }
}

@MiskTest(startService = true)
class MySqlIdTypeTest : IdTypeTest(DataSourceType.MYSQL)

@MiskTest(startService = true)
class VitessMySQLIdTypeTest : IdTypeTest(DataSourceType.VITESS_MYSQL)

@MiskTest(startService = true)
class PostgreSQLIdTypeTest : IdTypeTest(DataSourceType.POSTGRESQL)
