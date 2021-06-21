package misk.jooq.config

import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcTestingModule
import misk.jooq.JooqModule
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
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
        database = "misk_jooq_testing_writer",
        migrations_resource = "classpath:/db-migrations",
        show_sql = "true"
      ),
      reader = DataSourceConfig(
        type = DataSourceType.MYSQL,
        username = "root",
        password = "",
        database = "misk_jooq_testing_reader",
        migrations_resource = "classpath:/db-migrations",
        show_sql = "true"
      )
    )
    install(JooqModule(
      qualifier = JooqDBIdentifier::class,
      dataSourceClusterConfig = datasourceConfig,
      jooqCodeGenSchemaName = "jooq",
      jooqTimestampRecordListenerOptions = JooqTimestampRecordListenerOptions(
        install = true,
        createdAtColumnName = "created_at",
        updatedAtColumnName = "updated_at"
      ),
      readerQualifier = JooqDBReadOnlyIdentifier::class
    ))
    install(JdbcTestingModule(JooqDBIdentifier::class))
    install(LogCollectorModule())
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class JooqDBIdentifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class JooqDBReadOnlyIdentifier
