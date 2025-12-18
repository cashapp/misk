package misk.jdbc

import com.google.inject.ConfigurationException
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.util.Modules
import jakarta.inject.Qualifier
import kotlin.test.assertFailsWith
import misk.MiskTestingServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest(startService = false)
internal class JdbcModuleInstallSchemaMigratorTest {
  val deploymentModule = DeploymentModule(TESTING)

  val appConfig = MiskConfig.load<RootConfig>("test_schemamigrator_app", TESTING)
  val config = appConfig.mysql_data_source

  @Test
  fun `installSchemaMigrator=false should not bind SchemaMigratorService but should bind PingDatabaseService`() {
    val module =
      Modules.combine(
        deploymentModule,
        MiskTestingServiceModule(),
        JdbcModule(TestDb::class, config, installSchemaMigrator = false),
      )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should not be bound
    val exception =
      assertFailsWith<ConfigurationException> {
        injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
      }
    assertThat(exception.message).contains("No implementation for")
    assertThat(exception.message).contains("SchemaMigratorService")

    // PingDatabaseService should still be bound
    val pingDatabaseService = injector.getInstance(Key.get(PingDatabaseService::class.java, TestDb::class.java))
    assertThat(pingDatabaseService).isInstanceOf(PingDatabaseService::class.java)
  }

  @Test
  fun `default installSchemaMigrator should bind SchemaMigratorService`() {
    val module =
      Modules.combine(
        deploymentModule,
        MiskTestingServiceModule(),
        JdbcModule(TestDb::class, config), // Default should be true
      )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should be bound by default
    val schemaMigratorService = injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
    assertThat(schemaMigratorService).isNotNull()
  }

  @Test
  fun `EXTERNALLY_MANAGED migrations format should not bind SchemaMigratorService but should bind PingDatabaseService`() {
    val configWithExternalMigrations = config.copy(migrations_format = MigrationsFormat.EXTERNALLY_MANAGED)

    val module =
      Modules.combine(
        deploymentModule,
        MiskTestingServiceModule(),
        JdbcModule(TestDb::class, configWithExternalMigrations),
      )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should not be bound when migrations are externally managed
    val exception =
      assertFailsWith<ConfigurationException> {
        injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
      }
    assertThat(exception.message).contains("No implementation for")
    assertThat(exception.message).contains("SchemaMigratorService")

    // PingDatabaseService should still be bound
    val pingDatabaseService = injector.getInstance(Key.get(PingDatabaseService::class.java, TestDb::class.java))
    assertThat(pingDatabaseService).isInstanceOf(PingDatabaseService::class.java)
  }

  @Test
  fun `EXTERNALLY_MANAGED with installSchemaMigrator=true should still not bind SchemaMigratorService`() {
    val configWithExternalMigrations = config.copy(migrations_format = MigrationsFormat.EXTERNALLY_MANAGED)

    val module =
      Modules.combine(
        deploymentModule,
        MiskTestingServiceModule(),
        JdbcModule(
          qualifier = TestDb::class,
          config = configWithExternalMigrations,
          readerQualifier = null,
          readerConfig = null,
          installHealthCheck = true,
          installSchemaMigrator = true, // Even with this true, EXTERNALLY_MANAGED should prevent binding
        ),
      )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should not be bound when migrations are externally managed
    val exception =
      assertFailsWith<ConfigurationException> {
        injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
      }
    assertThat(exception.message).contains("No implementation for")
    assertThat(exception.message).contains("SchemaMigratorService")
  }

  data class RootConfig(
    val mysql_data_source: DataSourceConfig,
    val cockroachdb_data_source: DataSourceConfig,
    val postgresql_data_source: DataSourceConfig,
    val tidb_data_source: DataSourceConfig,
  ) : Config
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class TestDb
