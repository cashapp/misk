package misk.hibernate

import com.squareup.moshi.Moshi
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceType
import misk.jdbc.TruncateTablesService
import misk.jdbc.VitessScaleSafetyChecks
import misk.vitess.StartVitessService
import okhttp3.OkHttpClient
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Installs a service to clear the test datasource before running tests.
 *
 * TODO(jwilson): also create the database if it doesn't exist?
 */
class HibernateTestingModule(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig? = null,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf()
) : KAbstractModule() {
  override fun configure() {
    val truncateTablesServiceKey = TruncateTablesService::class.toKey(qualifier)

    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)

    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)

    if ((config == null || config.type == DataSourceType.VITESS)) {
      bindVitessChecks(transacterProvider)
    }

    install(ServiceModule(truncateTablesServiceKey)
        .dependsOn<SchemaMigratorService>(qualifier))
    bind(truncateTablesServiceKey).toProvider(Provider<TruncateTablesService> {
      TruncateTablesService(
          qualifier = qualifier,
          config = configProvider.get(),
          transacterProvider = transacterProvider,
          startUpStatements = startUpStatements,
          shutDownStatements = shutDownStatements
      )
    }).asSingleton()
  }

  private fun bindVitessChecks(transacterProvider: com.google.inject.Provider<Transacter>) {
    val startVitessServiceKey = StartVitessService::class.toKey(qualifier)
    val startVitessServiceProvider = getProvider(startVitessServiceKey)

    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)

    val vitessScaleSafetyChecksKey = VitessScaleSafetyChecks::class.toKey(qualifier)

    val moshiProvider = getProvider(Moshi::class.java)

    bind(vitessScaleSafetyChecksKey).toProvider(Provider<VitessScaleSafetyChecks> {
      VitessScaleSafetyChecks(
        config = configProvider.get(),
        moshi = moshiProvider.get(),
        okHttpClient = OkHttpClient(),
        startVitessService = startVitessServiceProvider.get(),
        transacter = transacterProvider.get()
      )
    }).asSingleton()

    multibind<DataSourceDecorator>(qualifier).to(vitessScaleSafetyChecksKey)
  }
}
