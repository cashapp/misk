package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.DependentService
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.PingDatabaseService
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
import javax.sql.DataSource
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
internal class SchemaMigratorTest {
  val defaultEnv = Environment.TESTING
  val config = MiskConfig.load<RootConfig>("test_schemamigrator_app", defaultEnv)

  @Singleton
  class DropTablesService @Inject constructor() : AbstractIdleService(), DependentService {
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
          statement.addBatch("DROP TABLE IF EXISTS library_table")
          statement.addBatch("DROP TABLE IF EXISTS merged_library_table")
          statement.executeBatch()
        }
      }
    }

    override fun shutDown() {}
  }

  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      multibind<Service>().to<DropTablesService>()
      multibind<Service>().toInstance(
          PingDatabaseService(Movies::class, config.data_source, defaultEnv))
      val dataSourceService =
          DataSourceService(Movies::class, config.data_source, defaultEnv,
              emptySet())
      multibind<Service>().toInstance(dataSourceService)
      bind<DataSource>().annotatedWith<Movies>().toProvider(dataSourceService)
      val injectorServiceProvider = getProvider(HibernateInjectorAccess::class.java)
      val sessionFactoryServiceKey = Key.get(SessionFactoryService::class.java, Movies::class.java)
      bind(sessionFactoryServiceKey).toProvider(Provider<SessionFactoryService> {
        SessionFactoryService(Movies::class, config.data_source, dataSourceService,
            injectorServiceProvider.get())
      }).asSingleton()
      bind<SessionFactory>().annotatedWith<Movies>().toProvider(sessionFactoryServiceKey)
      val sessionFactoryKey = Key.get(SessionFactory::class.java, Movies::class.java)
      val sessionFactoryProvider = getProvider(sessionFactoryKey)
      multibind<Service>().to(sessionFactoryServiceKey)
      val transacterKey = Key.get(Transacter::class.java, Movies::class.java)
      bind(transacterKey).toProvider(object : Provider<Transacter> {
        @Inject lateinit var queryTracingListener: QueryTracingListener
        override fun get(): RealTransacter = RealTransacter(
            Movies::class,
            sessionFactoryProvider,
            config.data_source,
            queryTracingListener,
            null
        )
      }).asSingleton()
    }
  }

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var transacter: Provider<Transacter>

  @Test fun initializeAndMigrate() {
    val schemaMigrator =
        SchemaMigrator(Movies::class, resourceLoader, transacter, config.data_source)

    val mainSource = config.data_source.migrations_resources!![0]
    val librarySource = config.data_source.migrations_resources!![1]

    resourceLoader.put("${mainSource}/v1002__movies.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${mainSource}/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())

    resourceLoader.put("${librarySource}/name/space/v1001__actors.sql", """
        |CREATE TABLE library_table (name varchar(255))
        |""".trimMargin())

    // Initially the schema_version table is absent.
    assertThat(tableExists("schema_version")).isFalse()
    assertThat(tableExists("table_1")).isFalse()
    assertThat(tableExists("table_2")).isFalse()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    assertThat(tableExists("library_table")).isFalse()
    assertThat(tableExists("merged_library_table")).isFalse()
    assertFailsWith<PersistenceException> {
      schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)
    }

    // Once we initialize, that table is present but empty.
    schemaMigrator.initialize()
    assertThat(schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)).isEmpty()
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isFalse()
    assertThat(tableExists("table_2")).isFalse()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    assertThat(tableExists("library_table")).isFalse()
    assertThat(tableExists("merged_library_table")).isFalse()

    // When we apply migrations, the table is present and contains the applied migrations.
    schemaMigrator.applyAll("SchemaMigratorTest", sortedSetOf())
    assertThat(schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)).containsExactly(
        NamedspacedMigration(1001),
        NamedspacedMigration(1002),
        NamedspacedMigration(1001, "name/space/"))
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isTrue()
    assertThat(tableExists("table_2")).isTrue()
    assertThat(tableExists("library_table")).isTrue()
    assertThat(tableExists("table_3")).isFalse()
    assertThat(tableExists("table_4")).isFalse()
    assertThat(tableExists("merged_library_table")).isFalse()
    schemaMigrator.requireAll()

    // When new migrations are added they can be applied.
    resourceLoader.put("${mainSource}/v1003__movies.sql", """
        |CREATE TABLE table_3 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${mainSource}/v1004__movies.sql", """
        |CREATE TABLE table_4 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${mainSource}/namespace/v1001__props.sql", """
        |CREATE TABLE merged_library_table (name varchar(255))
        |""".trimMargin())
    schemaMigrator.applyAll("SchemaMigratorTest", sortedSetOf(
        NamedspacedMigration(1001),
        NamedspacedMigration(1002),
        NamedspacedMigration(1001, "name/space/")))
    assertThat(schemaMigrator.appliedMigrations(Shard.SINGLE_SHARD)).containsExactly(
        NamedspacedMigration(1001),
        NamedspacedMigration(1002),
        NamedspacedMigration(1003),
        NamedspacedMigration(1004),
        NamedspacedMigration(1001, "name/space/"),
        NamedspacedMigration(1001, "namespace/"))
    assertThat(tableExists("schema_version")).isTrue()
    assertThat(tableExists("table_1")).isTrue()
    assertThat(tableExists("table_2")).isTrue()
    assertThat(tableExists("table_3")).isTrue()
    assertThat(tableExists("table_4")).isTrue()
    assertThat(tableExists("library_table")).isTrue()
    assertThat(tableExists("merged_library_table")).isTrue()
    schemaMigrator.requireAll()
  }

  @Test fun requireAllWithMissingMigrations() {
    val schemaMigrator =
        SchemaMigrator(Movies::class, resourceLoader, transacter, config.data_source)
    schemaMigrator.initialize()

    resourceLoader.put("${config.data_source.migrations_resources!![0]}/v1001__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${config.data_source.migrations_resources!![1]}/v1002__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())

    assertThat(assertFailsWith<IllegalStateException> {
      schemaMigrator.requireAll()
    }).hasMessage("""
          |Movies is missing migrations:
          |  ${config.data_source.migrations_resources!![0]}/v1001__foo.sql
          |  ${config.data_source.migrations_resources!![1]}/v1002__foo.sql""".trimMargin())
  }

  @Test fun errorOnDuplicateMigrations() {
    val schemaMigrator =
        SchemaMigrator(Movies::class, resourceLoader, transacter, config.data_source)

    resourceLoader.put("${config.data_source.migrations_resources!![0]}/v1001__foo.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${config.data_source.migrations_resources!![0]}/v1001__bar.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin())

    assertThat(assertFailsWith<IllegalArgumentException> {
      schemaMigrator.requireAll()
    }).hasMessageContaining("Duplicate migrations found")
  }

  @Test fun resourceVersionParsing() {
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__bar.sql")).isEqualTo(
        NamedspacedMigration(100, "foo/migrations/"))
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__v200.sql")).isEqualTo(
        NamedspacedMigration(100, "foo/migrations/"))
    assertThat(namespacedMigrationOrNull("v100_foo/migrations/v200__bar.sql")).isEqualTo(
        NamedspacedMigration(200, "v100_foo/migrations/"))
    assertThat(namespacedMigrationOrNull("v100_foo/migrations")).isNull()
    assertThat(namespacedMigrationOrNull("v100_foo/migrations/")).isNull()
    assertThat(namespacedMigrationOrNull("v100__bar.sql")).isEqualTo(NamedspacedMigration(100))
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__bar.SQL")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/V100__bar.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/v100_.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/migrations/v100__.sql")).isNull()
    assertThat(namespacedMigrationOrNull("foo/luv1__franklin.sql")).isNull()
  }

  private fun tableExists(table: String): Boolean {
    try {
      transacter.get().transaction { session ->
        session.hibernateSession.createNativeQuery("SELECT * FROM $table LIMIT 1").list()
      }
      return true
    } catch (e: PersistenceException) {
      return false
    }
  }

  private fun namespacedMigrationOrNull(resource: String): NamedspacedMigration? {
    try {
      return NamedspacedMigration.fromResourcePath(resource, "")
    } catch (expected: IllegalArgumentException) {
      return null
    }
  }

  data class RootConfig(val data_source: DataSourceConfig) : Config
}
