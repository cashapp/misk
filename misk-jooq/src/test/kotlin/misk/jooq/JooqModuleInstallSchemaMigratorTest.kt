package misk.jooq

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.util.Modules
import com.google.inject.ConfigurationException
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.SchemaMigratorService
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import kotlin.test.assertFailsWith

@MiskTest(startService = false)
internal class JooqModuleInstallSchemaMigratorTest {
  val deploymentModule = DeploymentModule(TESTING)

  val dataSourceConfig = DataSourceConfig(
    type = DataSourceType.MYSQL,
    username = "root",
    password = "",
    database = "misk_jooq_testing",
    migrations_resource = "classpath:/db-migrations",
    show_sql = "true"
  )

  val clusterConfig = DataSourceClusterConfig(
    writer = dataSourceConfig,
    reader = dataSourceConfig
  )

  @Test
  fun `installSchemaMigrator=false should not bind SchemaMigratorService but should bind DataSourceService`() {
    val module = Modules.combine(
      deploymentModule,
      MiskTestingServiceModule(),
      JooqModule(
        qualifier = TestDb::class,
        dataSourceClusterConfig = clusterConfig,
        jooqCodeGenSchemaName = "jooq",
        installSchemaMigrator = false
      )
    )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should not be bound
    val exception = assertFailsWith<ConfigurationException> {
      injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
    }
    assertThat(exception.message).contains("No implementation for")
    assertThat(exception.message).contains("SchemaMigratorService")

    // DataSourceService should still be bound
    val dataSourceService = injector.getInstance(Key.get(DataSourceService::class.java, TestDb::class.java))
    assertThat(dataSourceService).isInstanceOf(DataSourceService::class.java)
  }

  @Test
  fun `with reader qualifier and installSchemaMigrator=false should not bind SchemaMigratorService`() {
    val module = Modules.combine(
      deploymentModule,
      MiskTestingServiceModule(),
      JooqModule(
        qualifier = TestDb::class,
        dataSourceClusterConfig = clusterConfig,
        jooqCodeGenSchemaName = "jooq",
        readerQualifier = TestDbReader::class,
        installSchemaMigrator = false
      )
    )

    val injector = Guice.createInjector(module)

    // SchemaMigratorService should not be bound
    val exception = assertFailsWith<ConfigurationException> {
      injector.getInstance(Key.get(SchemaMigratorService::class.java, TestDb::class.java))
    }
    assertThat(exception.message).contains("No implementation for")
    assertThat(exception.message).contains("SchemaMigratorService")
  }
}

@com.google.inject.BindingAnnotation
internal annotation class TestDb

@com.google.inject.BindingAnnotation
internal annotation class TestDbReader
