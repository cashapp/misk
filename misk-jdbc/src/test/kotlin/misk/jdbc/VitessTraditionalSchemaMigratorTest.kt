package misk.jdbc

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.Keyspace
import misk.vitess.Shard
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.config.Config
import wisp.deployment.TESTING
import java.sql.Connection
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import misk.testing.MiskExternalDependency
import misk.vitess.testing.DefaultSettings
import misk.vitess.testing.utilities.DockerVitess
import java.sql.DriverManager

@MiskTest(startService = true)
internal class VitessTraditionalSchemaMigratorTest {
  val deploymentModule = DeploymentModule(TESTING)
  val config =
    MiskConfig.load<MoviesConfig>("test_schemamigrator_vitess_app", TESTING)

  @MiskExternalDependency private val dockerVitess = DockerVitess
  @MiskTestModule
  val module = Modules.combine(
    deploymentModule,
    MiskTestingServiceModule(),
    JdbcModule(Movies::class, config.data_source)
  )

  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator
  private lateinit var traditionalSchemaMigrator : TraditionalSchemaMigrator

  private val shard = Shard(Keyspace("movies_sharded"), "-80")

  @BeforeEach fun createSchemaMigrationTable() {
    traditionalSchemaMigrator = schemaMigrator as TraditionalSchemaMigrator
    traditionalSchemaMigrator.initialize()
  }

  @AfterEach fun cleanUpMigrationTable() {
    openDirectConnection()?.use { c ->
      c.createStatement().execute("USE `movies_sharded/-80`")
      c.prepareStatement("DELETE FROM `schema_version`").executeUpdate()
    }
  }

  @Test fun availableMigrations() {
    val migrations = traditionalSchemaMigrator.availableMigrations(Keyspace("movies_sharded"))
    assertThat(migrations.map { it.version }).containsAll(listOf(4, 5, 6, 7, 8))
  }

  @Test fun appliedMigrations() {
    assertThat(traditionalSchemaMigrator.appliedMigrations(shard).map { it.version }).isEmpty()

    insertSchemaMigration("1")
    assertThat(traditionalSchemaMigrator.appliedMigrations(shard).map { it.version }).contains(1)
  }

  @Test fun requireAll() {
    traditionalSchemaMigrator.initialize()

    assertThat(traditionalSchemaMigrator.appliedMigrations(shard).map { it.version }).isEmpty()

    insertSchemaMigration("4")
    insertSchemaMigration("5")
    insertSchemaMigration("6")
    insertSchemaMigration("7")
    insertSchemaMigration("8")
    // Test that this does not throw an exception
    traditionalSchemaMigrator.requireAll(shard)
  }

  private fun insertSchemaMigration(version: String) {
    // The schema_version is unknown to Vitess which means we can query it with shard targetting
    // but we can't insert into it (unless we specify -queryserver-config-allowunsafe-dmls which
    // vttestserver currently does not). So we bypass Vitess to insert into it directly.
    openDirectConnection()?.use { c ->
      c.createStatement().execute("USE `movies_sharded/-80`")
      val schemaVersion = c.prepareStatement(
        """
              |INSERT INTO `schema_version` (version, installed_by) VALUES (?, ?)
              |""".trimMargin()
      )
      schemaVersion.setString(1, version)
      schemaVersion.setString(2, "test")
      schemaVersion.executeUpdate()
    }
  }

  /** Open a direct connection to the Vitess MySQL instance. */
  private fun openDirectConnection(): Connection? {
    val vtgatePort = config.data_source.port ?: DefaultSettings.PORT
    val url = "jdbc:mysql://localhost:$vtgatePort/@primary"
    val dataSourceConfig = config.data_source
    val connection = DriverManager.getConnection(url, dataSourceConfig.username, dataSourceConfig.password)
    return connection
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class Movies

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class Movies2

internal data class MoviesConfig(
  val data_source: DataSourceConfig,
) : Config
