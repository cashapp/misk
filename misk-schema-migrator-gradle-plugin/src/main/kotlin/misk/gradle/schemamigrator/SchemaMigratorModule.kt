package misk.gradle.schemamigrator

import misk.MiskCommonServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
import misk.resources.ResourceLoaderModule
import wisp.deployment.TESTING
import java.io.File
import java.time.Clock

class SchemaMigratorModule(
  private val database: String,
  private val host: String?,
  private val port: Int?,
  private val dbType: String,
  private val username: String,
  private val password: String,
  private val schemaDir: File
): KAbstractModule() {

  override fun configure() {
    val schemaMigratorClusterConfig = DataSourceConfig(
      host = host,
      port = port,
      type = DataSourceType.valueOf(dbType),
      migrations_resource = "filesystem:$schemaDir",
      database = database,
      username = username,
      password = password,
    )

    bind<Clock>().toInstance(Clock.systemUTC())
    install(ResourceLoaderModule())
    install(MiskCommonServiceModule())
    install(DeploymentModule(TESTING))
    install(
      JdbcModule(
        qualifier = SchemaMigratorDatabase::class,
        config = schemaMigratorClusterConfig,
        databasePool = RealDatabasePool
      )
    )
  }
}
