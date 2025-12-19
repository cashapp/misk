package misk.jdbc

import com.google.inject.Provider
import kotlin.reflect.KClass
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.testing.TestFixture
import misk.time.ForceUtcTimeZoneService

/**
 * Installs a service to clear the test datasource before running tests. This module should be installed alongside the
 * [JdbcModule].
 *
 * If you run your tests in parallel, you need to install the [JdbcModule] as follows to ensure that your test suites do
 * not share databases concurrently:
 *
 *     install(JdbcModule(MyDatabase::class, dataSourceConfig, SHARED_TEST_DATABASE_POOL))
 *
 * See [misk.jdbc.SHARED_TEST_DATABASE_POOL].
 */
class JdbcTestingModule(
  private val qualifier: KClass<out Annotation>,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf(),
  // TODO: default to opt-out once these are ready for prime time.
  private val scaleSafetyChecks: Boolean = false,
) : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<ForceUtcTimeZoneService>())

    val truncateTablesServiceKey = TruncateTablesService::class.toKey(qualifier)
    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))

    install(
      ServiceModule(truncateTablesServiceKey).dependsOn<SchemaMigratorService>(qualifier).enhancedBy<ReadyService>()
    )
    multibind<TestFixture>()
      .toProvider {
        JdbcTestFixture(
          qualifier = qualifier,
          dataSourceService = dataSourceServiceProvider.get(),
          transacterProvider = transacterProvider,
        )
      }
      .asSingleton()
    bind(truncateTablesServiceKey)
      .toProvider {
        TruncateTablesService(
          qualifier = qualifier,
          dataSourceService = dataSourceServiceProvider.get(),
          transacterProvider = transacterProvider,
          startUpStatements = startUpStatements,
          shutDownStatements = shutDownStatements,
        )
      }
      .asSingleton()

    if (scaleSafetyChecks) bindScaleSafetyChecks()
  }

  private fun bindScaleSafetyChecks() {
    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)

    val mySqlScaleSafetyChecks = MySqlScaleSafetyChecks::class.toKey(qualifier)
    bind(mySqlScaleSafetyChecks)
      .toProvider(Provider { MySqlScaleSafetyChecks(config = configProvider.get()) })
      .asSingleton()

    multibind<DataSourceDecorator>(qualifier).to(mySqlScaleSafetyChecks)
  }
}

inline fun <reified T : Annotation> JdbcTestingModule(
  startUpStatements: List<String> = listOf(),
  shutDownStatements: List<String> = listOf(),
  scaleSafetyChecks: Boolean = false,
) = JdbcTestingModule(T::class, startUpStatements, shutDownStatements, scaleSafetyChecks)
