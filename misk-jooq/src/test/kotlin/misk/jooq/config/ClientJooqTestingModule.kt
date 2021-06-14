package misk.jooq.config

import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcTestingModule
import misk.jooq.JooqModule
import misk.logging.LogCollectorModule
import wisp.deployment.TESTING
import javax.inject.Qualifier

class ClientJooqTestingModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(MiskTestingServiceModule())

    val datasourceConfig = DataSourceClusterConfig(
      writer = DataSourceConfig(
        type = DataSourceType.MYSQL,
        username = "root",
        password = "",
        database = "misk_jooq_testing",
        migrations_resource = "classpath:/db-migrations",
        show_sql = "true"
      ),
      reader = null
    )
    install(JooqModule(JooqDBIdentifier::class, datasourceConfig))
    install(JdbcTestingModule(JooqDBIdentifier::class))
    install(LogCollectorModule())
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class JooqDBIdentifier
