package misk.hibernate

import com.squareup.moshi.Moshi
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceConnector
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceType
import misk.jdbc.MySqlScaleSafetyChecks
import misk.jdbc.TruncateTablesService
import misk.jdbc.VitessScaleSafetyChecks
import misk.time.ForceUtcTimeZoneService
import misk.database.StartDatabaseService
import misk.jdbc.SchemaMigratorService
import okhttp3.OkHttpClient
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Installs a service to clear the test datasource before running tests. This module should be
 * installed alongside the [HibernateModule].
 *
 * If you run your tests in parallel, you need to install the [HibernateModule] as follows to
 * ensure that your test suites do not share databases concurrently:
 *
 *     install(HibernateModule(MyDatabase::class, dataSourceConfig, SHARED_TEST_DATABASE_POOL))
 *
 * See [misk.jdbc.SHARED_TEST_DATABASE_POOL].
 */
class HibernateTestingModule(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig? = null,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf(),
  // TODO: default to opt-out once these are ready for prime time.
  private val scaleSafetyChecks: Boolean = false
) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<ForceUtcTimeZoneService>())

    val truncateTablesServiceKey = TruncateTablesService::class.toKey(qualifier)

    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)

    if (scaleSafetyChecks) bindScaleSafetyChecks(transacterProvider)

    val dataSourceConnector = getProvider(keyOf<DataSourceConnector>(qualifier))
    install(ServiceModule(truncateTablesServiceKey)
        .dependsOn<SchemaMigratorService>(qualifier))
    bind(truncateTablesServiceKey).toProvider(Provider {
      TruncateTablesService(
          qualifier = qualifier,
          connector = dataSourceConnector.get(),
          transacterProvider = transacterProvider,
          startUpStatements = startUpStatements,
          shutDownStatements = shutDownStatements
      )
    }).asSingleton()
  }

  private fun bindScaleSafetyChecks(transacterProvider: com.google.inject.Provider<Transacter>) {
    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)
    val moshiProvider = getProvider(Moshi::class.java)

    if (config == null || config.type == DataSourceType.VITESS || config.type == DataSourceType.VITESS_MYSQL) {
      val startVitessServiceKey = StartDatabaseService::class.toKey(qualifier)
      val startVitessServiceProvider = getProvider(startVitessServiceKey)
      val vitessScaleSafetyChecksKey = VitessScaleSafetyChecks::class.toKey(qualifier)

      bind(vitessScaleSafetyChecksKey).toProvider(Provider {
        VitessScaleSafetyChecks(
          config = configProvider.get(),
          moshi = moshiProvider.get(),
          okHttpClient = OkHttpClient(),
          startDatabaseService = startVitessServiceProvider.get(),
          transacter = transacterProvider.get()
        )
      }).asSingleton()

      multibind<DataSourceDecorator>(qualifier).to(vitessScaleSafetyChecksKey)
    } else if (config.type == DataSourceType.MYSQL) {
      val mySqlScaleSafetyChecks = MySqlScaleSafetyChecks::class.toKey(qualifier)
      bind(mySqlScaleSafetyChecks).toProvider(Provider {
        MySqlScaleSafetyChecks(
          config = configProvider.get(),
          transacter = transacterProvider.get()
        )
      }).asSingleton()

      multibind<DataSourceDecorator>(qualifier).to(mySqlScaleSafetyChecks)
    }
  }
}
