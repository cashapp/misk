package misk.jdbc

import com.squareup.moshi.Moshi
import javax.inject.Provider
import kotlin.reflect.KClass
import misk.ServiceModule
import misk.database.StartDatabaseService
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.time.ForceUtcTimeZoneService
import misk.vitess.VitessScaleSafetyChecks
import okhttp3.OkHttpClient

/**
 * Installs a service to clear the test datasource before running tests. This module should be
 * installed alongside the [JdbcModule].
 *
 * If you run your tests in parallel, you need to install the [JdbcModule] as follows to
 * ensure that your test suites do not share databases concurrently:
 *
 *     install(JdbcModule(MyDatabase::class, dataSourceConfig, SHARED_TEST_DATABASE_POOL))
 *
 * See [misk.jdbc.SHARED_TEST_DATABASE_POOL].
 */
@Deprecated("Replace the dependency on misk-jdcb-testing with testFixtures(misk-jdbc)")
class JdbcTestingModule(
  private val qualifier: KClass<out Annotation>,
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
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))

    install(
      ServiceModule(truncateTablesServiceKey)
        .enhances<SchemaMigratorService>(qualifier)
    )
    bind(truncateTablesServiceKey).toProvider(Provider {
      TruncateTablesService(
        qualifier = qualifier,
        dataSourceService = dataSourceServiceProvider.get(),
        transacterProvider = transacterProvider,
        startUpStatements = startUpStatements,
        shutDownStatements = shutDownStatements
      )
    }).asSingleton()

    if (scaleSafetyChecks) bindScaleSafetyChecks()
  }

  private fun bindScaleSafetyChecks() {
    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)
    val moshiProvider = getProvider(Moshi::class.java)

    val startVitessServiceKey = StartDatabaseService::class.toKey(qualifier)
    val startVitessServiceProvider = getProvider(startVitessServiceKey)
    val vitessScaleSafetyChecksKey = VitessScaleSafetyChecks::class.toKey(qualifier)

    bind(vitessScaleSafetyChecksKey).toProvider(Provider {
      VitessScaleSafetyChecks(
        config = configProvider.get(),
        moshi = moshiProvider.get(),
        okHttpClient = OkHttpClient(),
        startDatabaseService = startVitessServiceProvider.get(),
      )
    }).asSingleton()

    multibind<DataSourceDecorator>(qualifier).to(vitessScaleSafetyChecksKey)
    val mySqlScaleSafetyChecks = MySqlScaleSafetyChecks::class.toKey(qualifier)
    bind(mySqlScaleSafetyChecks).toProvider(Provider {
      MySqlScaleSafetyChecks(
        config = configProvider.get(),
      )
    }).asSingleton()

    multibind<DataSourceDecorator>(qualifier).to(mySqlScaleSafetyChecks)
  }
}
