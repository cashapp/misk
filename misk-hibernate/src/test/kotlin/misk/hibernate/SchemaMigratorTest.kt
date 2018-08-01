package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.DependentService
import misk.MiskServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import javax.persistence.PersistenceException
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
internal class SchemaMigratorTest {
  val defaultEnv = Environment.TESTING
  val config = MiskConfig.load<RootConfig>("test_schemamigrator_app", defaultEnv)

  @Singleton
  class DropTablesService : AbstractIdleService(), DependentService {
    @Inject @Movies lateinit var sessionFactoryProvider: Provider<SessionFactory>

    override val consumedKeys = setOf<Key<*>>(SessionFactoryService::class.toKey(Movies::class))
    override val producedKeys = setOf<Key<*>>()

    override fun startUp() {
      sessionFactoryProvider.get().openSession().use { session ->
        session.doReturningWork { connection ->
          val statement = connection.createStatement()
          statement.addBatch("DROP TABLE IF EXISTS schema_version")
          statement.addBatch("DROP TABLE IF EXISTS table_1")
          statement.addBatch("DROP TABLE IF EXISTS table_2")
          statement.addBatch("DROP TABLE IF EXISTS table_3")
          statement.addBatch("DROP TABLE IF EXISTS table_4")
          statement.executeBatch()
        }
      }
    }

    override fun shutDown() {}
  }

  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskServiceModule())
      multibind<Service>().to<DropTablesService>()
      multibind<Service>().toInstance(
          PingDatabaseService(Movies::class, config.data_source, defaultEnv))
      val sessionFactoryService =
          SessionFactoryService(Movies::class, config.data_source, defaultEnv)
      multibind<Service>().toInstance(sessionFactoryService)
      bind<SessionFactory>().annotatedWith<Movies>().toProvider(sessionFactoryService)
    }
  }

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var sessionFactory: SessionFactory

  @Test fun initializeAndMigrate() {
    val schemaMigrator =
        SchemaMigrator(Movies::class, resourceLoader, sessionFactory, config.data_source)

    resourceLoader.put("${config.data_source.migrations_resource}/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${config.data_source.migrations_resource}/v1002__movies.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin())

    // Initially the schema_version table is absent.
    assertThat(tableExists("schema_version")).isFalse()
    assertThat(tableExists("table_1")).isFalse()
    assertThat(tableExists("table_2")).isFalse()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    assertFailsWith<PersistenceException> {
      schemaMigrator.appliedMigrations()
    }

    // Once we initialize, that table is present but empty.
    schemaMigrator.initialize()
    assertThat(schemaMigrator.appliedMigrations()).isEmpty()
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isFalse()
    assertThat(tableExists("table_2")).isFalse()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()

    // When we apply migrations, the table is present and contains the applied migrations.
    schemaMigrator.applyAll("SchemaMigratorTest", setOf())
    assertThat(schemaMigrator.appliedMigrations()).containsExactly(1001, 1002)
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isTrue()
    assertThat(tableExists("table_2")).isTrue()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    schemaMigrator.requireAll()

    // When new migrations are added they can be applied.
    resourceLoader.put("${config.data_source.migrations_resource}/v1003__movies.sql", """
        |CREATE TABLE table_3 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${config.data_source.migrations_resource}/v1004__movies.sql", """
        |CREATE TABLE table_4 (name varchar(255))
        |""".trimMargin())
    schemaMigrator.applyAll("SchemaMigratorTest", setOf(1001, 1002))
    assertThat(schemaMigrator.appliedMigrations()).containsExactly(1001, 1002, 1003, 1004)
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isTrue()
    assertThat(tableExists("table_2")).isTrue()
    assertThat(tableExists("table_3")).isTrue()
    assertThat(tableExists("table_4")).isTrue()
    schemaMigrator.requireAll()
  }

  @Test fun requireAllWithMissingMigrations() {
    val schemaMigrator =
        SchemaMigrator(Movies::class, resourceLoader, sessionFactory, config.data_source)
    schemaMigrator.initialize()

    resourceLoader.put("${config.data_source.migrations_resource}/v1001__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${config.data_source.migrations_resource}/v1002__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())

    assertThat(assertFailsWith<IllegalStateException> {
      schemaMigrator.requireAll()
    }).hasMessage("""
          |Movies is missing migrations:
          |  ${config.data_source.migrations_resource}/v1001__foo.sql
          |  ${config.data_source.migrations_resource}/v1002__foo.sql""".trimMargin())
  }

  @Test fun resourceVersionParsing() {
    val sm = SchemaMigrator(Movies::class, resourceLoader, sessionFactory, config.data_source)

    assertThat(sm.resourceVersionOrNull("foo/migrations/v100__bar.sql")).isEqualTo(100)
    assertThat(sm.resourceVersionOrNull("foo/migrations/v100__v200.sql")).isEqualTo(100)
    assertThat(sm.resourceVersionOrNull("v100_foo/migrations/v200__bar.sql")).isEqualTo(200)
    assertThat(sm.resourceVersionOrNull("v100_foo/migrations")).isNull()
    assertThat(sm.resourceVersionOrNull("v100_foo/migrations/")).isNull()
    assertThat(sm.resourceVersionOrNull("v100__bar.sql")).isEqualTo(100)
    assertThat(sm.resourceVersionOrNull("foo/migrations/v100__bar.SQL")).isNull()
    assertThat(sm.resourceVersionOrNull("foo/migrations/V100__bar.sql")).isNull()
    assertThat(sm.resourceVersionOrNull("foo/migrations/v100_.sql")).isNull()
    assertThat(sm.resourceVersionOrNull("foo/migrations/v100__.sql")).isNull()
    assertThat(sm.resourceVersionOrNull("foo/migrations/v100__.sql")).isNull()
  }

  private fun tableExists(table: String): Boolean {
    try {
      sessionFactory.openSession().use { session ->
        session.createNativeQuery("SELECT * FROM $table LIMIT 1").list()
      }
      return true
    } catch (e: PersistenceException) {
      return false
    }
  }

  private fun SchemaMigrator.resourceVersionOrNull(resource: String): Int? {
    try {
      return resourceVersion(resource)
    } catch (expected: IllegalArgumentException) {
      return null
    }
  }

  data class RootConfig(val data_source: DataSourceConfig) : Config
}
