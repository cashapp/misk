package misk.jooq.config

import java.time.Clock
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jooq.IsolationLevelAwareConnectionProvider
import misk.jooq.JooqTransacter
import misk.jooq.listeners.AvoidUsingSelectStarListener
import misk.jooq.listeners.JooqSQLLogger
import misk.jooq.listeners.JooqTimestampRecordListener
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import org.jooq.Configuration
import org.jooq.SQLDialect
import org.jooq.conf.MappedSchema
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultExecuteListenerProvider
import org.jooq.impl.DefaultTransactionProvider
import org.jooq.kotlin.renderMapping
import org.jooq.kotlin.schemata
import org.jooq.kotlin.settings

internal abstract class ConfigurationFactory {
  abstract fun getConfiguration(options: JooqTransacter.TransacterOptions): Configuration

  protected fun buildConfiguration(
    clock: Clock,
    dataSourceConfig: DataSourceConfig,
    dataSourceService: DataSourceService,
    jooqCodeGenSchemaName: String,
    jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions,
    options: JooqTransacter.TransacterOptions,
  ): Configuration {
    val settings = settings {
      isExecuteWithOptimisticLocking = true
      renderMapping {
        schemata { add(MappedSchema().withInput(jooqCodeGenSchemaName).withOutput(dataSourceConfig.database)) }
      }
    }
    val connectionProvider =
      IsolationLevelAwareConnectionProvider(
        dataSourceConnectionProvider = DataSourceConnectionProvider(dataSourceService.dataSource),
        transacterOptions = options,
      )
    return DefaultConfiguration().apply {
      set(settings)
      set(dataSourceConfig.type.toSqlDialect())
      set(connectionProvider)
      set(DefaultTransactionProvider(connectionProvider, false))
      val executeListeners = buildList {
        add(DefaultExecuteListenerProvider(AvoidUsingSelectStarListener()))
        if (dataSourceConfig.show_sql.toBoolean()) {
          add(DefaultExecuteListenerProvider(JooqSQLLogger()))
        }
      }
      set(*executeListeners.toTypedArray())

      if (jooqTimestampRecordListenerOptions.install) {
        set(
          JooqTimestampRecordListener(
            clock = clock,
            createdAtColumnName = jooqTimestampRecordListenerOptions.createdAtColumnName,
            updatedAtColumnName = jooqTimestampRecordListenerOptions.updatedAtColumnName,
          )
        )
      }
    }
  }

  protected fun DataSourceType.toSqlDialect() =
    when (this) {
      DataSourceType.MYSQL -> SQLDialect.MYSQL
      DataSourceType.HSQLDB -> SQLDialect.HSQLDB
      DataSourceType.VITESS_MYSQL -> SQLDialect.MYSQL
      DataSourceType.POSTGRESQL -> SQLDialect.POSTGRES
      DataSourceType.TIDB -> SQLDialect.MYSQL
      else -> throw IllegalArgumentException("no SQLDialect for " + this.name)
    }
}
