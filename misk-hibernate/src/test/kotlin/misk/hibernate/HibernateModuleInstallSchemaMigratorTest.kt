package misk.hibernate

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.ConfigurationException
import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.util.Modules
import kotlin.test.assertFailsWith
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceService
import misk.jdbc.SchemaMigratorService
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

@MiskTest(startService = false)
internal class HibernateModuleInstallSchemaMigratorTest {
  val deploymentModule = DeploymentModule(TESTING)

  val config = MiskConfig.load<MoviesConfig>("moviestestmodule", TESTING)
  val dataSourceConfig = config.mysql_data_source

  @Test
  fun `installSchemaMigrator=false should not bind SchemaMigratorService but should bind DataSourceService`() {
    val module =
      Modules.combine(
        deploymentModule,
        MiskTestingServiceModule(),
        HibernateModule(
          qualifier = TestDb::class,
          config = dataSourceConfig,
          readerQualifier = null,
          readerConfig = null,
          installSchemaMigrator = false,
        ),
        object : HibernateEntityModule(TestDb::class) {
          override fun configureHibernate() {
            addEntities(DbMovie::class)
          }
        },
      )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should not be bound
    val exception =
      assertFailsWith<ConfigurationException> {
        injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
      }
    assertThat(exception.message).contains("No implementation for")
    assertThat(exception.message).contains("SchemaMigratorService")

    // DataSourceService should still be bound
    val dataSourceService = injector.getInstance(Key.get(DataSourceService::class.java, TestDb::class.java))
    assertThat(dataSourceService).isInstanceOf(DataSourceService::class.java)
  }

  @Test
  fun `cluster config with installSchemaMigrator=false should not bind SchemaMigratorService`() {
    val clusterConfig = DataSourceClusterConfig(writer = dataSourceConfig, reader = dataSourceConfig)

    val module =
      Modules.combine(
        deploymentModule,
        MiskTestingServiceModule(),
        HibernateModule(
          qualifier = TestDb::class,
          readerQualifier = TestDbReader::class,
          cluster = clusterConfig,
          installSchemaMigrator = false,
        ),
        object : HibernateEntityModule(TestDb::class) {
          override fun configureHibernate() {
            addEntities(DbMovie::class)
          }
        },
      )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should not be bound
    val exception =
      assertFailsWith<ConfigurationException> {
        injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
      }
    assertThat(exception.message).contains("No implementation for")
    assertThat(exception.message).contains("SchemaMigratorService")
  }

  @Test
  fun `installSchemaMigrator=false should allow ServiceManager to be created`() {
    val module =
      Modules.combine(
        deploymentModule,
        MiskTestingServiceModule(),
        HibernateModule(
          qualifier = TestDb::class,
          config = dataSourceConfig,
          readerQualifier = null,
          readerConfig = null,
          installSchemaMigrator = false,
        ),
        object : HibernateEntityModule(TestDb::class) {
          override fun configureHibernate() {
            addEntities(DbMovie::class)
          }
        },
      )

    val injector = Guice.createInjector(module)

    // ServiceManager should be created successfully without SchemaMigratorService
    val serviceManager = injector.getInstance(ServiceManager::class.java)
    assertThat(serviceManager).isNotNull()

    // DataSourceService should still be bound
    val dataSourceService = injector.getInstance(Key.get(DataSourceService::class.java, TestDb::class.java))
    assertThat(dataSourceService).isInstanceOf(DataSourceService::class.java)
  }
}

@com.google.inject.BindingAnnotation internal annotation class TestDb

@com.google.inject.BindingAnnotation internal annotation class TestDbReader
