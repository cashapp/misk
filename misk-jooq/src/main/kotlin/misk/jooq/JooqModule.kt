package misk.jooq

import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DatabasePool
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.MappedSchema
import org.jooq.conf.RenderMapping
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DefaultTransactionProvider
import java.time.Clock
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

class JooqModule(
  private val qualifier: KClass<out Annotation>,
  private val dataSourceClusterConfig: DataSourceClusterConfig,
  private val databasePool: DatabasePool = RealDatabasePool,
  private val readerQualifier: KClass<out Annotation>? = null
) : KAbstractModule() {

  override fun configure() {
    install(
      JdbcModule(
        qualifier,
        dataSourceClusterConfig.writer,
        readerQualifier,
        dataSourceClusterConfig.reader,
        databasePool
      )
    )

    val transacterKey = JooqTransacter::class.toKey(qualifier)
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))
    bind(transacterKey).toProvider(
      Provider {
        JooqTransacter(
          dslContext = dslContext(dataSourceServiceProvider.get())
        )
      }
    ).asSingleton()

    val jooqTransacterProvider = getProvider(keyOf<JooqTransacter>(qualifier))
    val healthCheckKey = keyOf<HealthCheck>(qualifier)
    bind(healthCheckKey)
      .toProvider(object : Provider<JooqHealthCheck> {
        @Inject
        lateinit var clock: Clock

        override fun get(): JooqHealthCheck {
          return JooqHealthCheck(
            qualifier,
            dataSourceServiceProvider,
            jooqTransacterProvider,
            clock
          )
        }
      })
      .asSingleton()
    multibind<HealthCheck>().to(healthCheckKey)
  }

  private fun dslContext(
    dataSourceService: DataSourceService
  ): DSLContext {
    val settings = Settings()
      .withExecuteWithOptimisticLocking(true)
      .withRenderMapping(
        RenderMapping().withSchemata(
          MappedSchema()
            .withInput("jooq") // change this to be the schema name used in the code gen
            .withOutput(dataSourceClusterConfig.writer.database)
        )
      )
    return DSL.using(dataSourceService.get(), SQLDialect.MYSQL, settings).apply {
      configuration().set(
        DefaultTransactionProvider(
          configuration().connectionProvider(),
          false
        )
      ).apply {
        if ("true" == dataSourceClusterConfig.writer.show_sql) {
          set(JooqSQLLogger())
        }
      }
    }
  }
}
