package misk.jdbc

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.database.DockerVitessCluster
import misk.database.StartDatabaseService
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
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest(startService = true)
internal class VitessSchemaMigratorTest {
  val deploymentModule = DeploymentModule(TESTING)
  val config =
    MiskConfig.load<MoviesConfig>("test_schemamigrator_vitess_app", TESTING)

  @MiskTestModule
  val module = Modules.combine(
    deploymentModule,
    MiskTestingServiceModule(),
    JdbcModule(Movies::class, config.data_source)
  )

  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator
  @Inject @Movies lateinit var databaseService: StartDatabaseService

  private val shard = Shard(Keyspace("movies"), "-80")

  @BeforeEach fun createSchemaMigrationTable() {
    schemaMigrator.initialize()
  }

  @AfterEach fun cleanUpMigrationTable() {
    openDirectConnection()?.use { c ->
      val schemaVersion = c.prepareStatement(
        """
            |DELETE FROM `vt_movies_-80`.schema_version
            |""".trimMargin()
      )
      schemaVersion.executeUpdate()
    }
  }

  @Test fun availableMigrations() {
    val migrations = schemaMigrator.availableMigrations(Keyspace("movies"))
    assertThat(migrations.map { it.version }).containsAll(listOf(1, 2))
  }

  @Test fun appliedMigrations() {
    assertThat(schemaMigrator.appliedMigrations(shard).map { it.version }).isEmpty()

    insertSchemaMigration("1")
    assertThat(schemaMigrator.appliedMigrations(shard).map { it.version }).contains(1)
  }

  @Test fun requireAll() {
    schemaMigrator.initialize()

    assertThat(schemaMigrator.appliedMigrations(shard).map { it.version }).isEmpty()

    insertSchemaMigration("1")
    insertSchemaMigration("2")
    // Test that this does not throw an exception
    schemaMigrator.requireAll(shard)
  }

  private fun insertSchemaMigration(version: String) {
    // The schema_version is unknown to Vitess which means we can query it with shard targetting
    // but we can't insert into it (unless we specify -queryserver-config-allowunsafe-dmls which
    // vttestserver currently does not). So we bypass Vitess to insert into it directly.
    openDirectConnection()?.use { c ->
      val schemaVersion = c.prepareStatement(
        """
              |INSERT INTO `vt_movies_-80`.schema_version (version, installed_by) VALUES (?, ?)
              |""".trimMargin()
      )
      schemaVersion.setString(1, version)
      schemaVersion.setString(2, "test")
      schemaVersion.executeUpdate()
    }
  }

  /** Open a direct connection to the Vitess MySQL instance. */
  private fun openDirectConnection(): Connection? {
    val cluster = databaseService.server?.let { (it as DockerVitessCluster).cluster }
    return cluster?.openMysqlConnection()
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class Movies

internal data class MoviesConfig(
  val data_source: DataSourceConfig,
) : Config
