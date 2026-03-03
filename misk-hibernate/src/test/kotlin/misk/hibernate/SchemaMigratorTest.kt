package misk.hibernate

import com.google.common.collect.Iterables.getOnlyElement
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Provider
import javax.persistence.PersistenceException
import kotlin.test.assertFailsWith

@MiskTest(startService = false)
internal class SchemaMigratorTest {
  val defaultEnv = Environment.TESTING
  val config = MiskConfig.load<RootConfig>("test_schemamigrator_app", defaultEnv)

  @MiskTestModule
  val module = Modules.combine(
      EnvironmentModule(Environment.TESTING),
      MiskTestingServiceModule(),
      HibernateModule(Movies::class, config.data_source)
  )

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var transacter: Provider<Transacter>
  @Inject @Movies lateinit var dataSourceService: DataSourceService
  @Inject @Movies lateinit var sessionFactoryService: SessionFactoryService
  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator
  @Inject @Movies lateinit var schemaMigratorService: SchemaMigratorService

  @AfterEach
  internal fun tearDown() {
    if (sessionFactoryService.isRunning) {
      sessionFactoryService.get().openSession().use { session ->
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
  }

  @BeforeEach internal fun setUp() {
    dataSourceService.startAsync()
    dataSourceService.awaitRunning()
    sessionFactoryService.startAsync()
    sessionFactoryService.awaitRunning()
  }

  @Test fun initializeAndMigrate() {
    val mainSource = config.data_source.migrations_resources!![0]
    val librarySource = config.data_source.migrations_resources!![1]

    resourceLoader.put("$mainSource/v1002__movies.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("$mainSource/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())

    resourceLoader.put("$librarySource/name/space/v1001__actors.sql", """
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
    resourceLoader.put("$mainSource/v1003__movies.sql", """
        |CREATE TABLE table_3 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("$mainSource/v1004__movies.sql", """
        |CREATE TABLE table_4 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("$mainSource/namespace/v1001__props.sql", """
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

  @Test fun healthChecks() {
    resourceLoader.put("${config.data_source.migrations_resources!![0]}/v1002__movies.sql", """
        |CREATE TABLE table_2 (name varchar(255))
        |""".trimMargin())
    resourceLoader.put("${config.data_source.migrations_resources!![0]}/v1001__movies.sql", """
        |CREATE TABLE table_1 (name varchar(255))
        |""".trimMargin())

    schemaMigratorService.startAsync()
    schemaMigratorService.awaitRunning()

    assertThat(getOnlyElement(schemaMigratorService.status().messages)).isEqualTo(
        "SchemaMigratorService: Movies is migrated: " +
            "MigrationState(shards={keyspace/0=(all 2 migrations applied)})")
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
