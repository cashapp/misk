package misk.hibernate

import com.google.common.util.concurrent.Service
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceDecorator
import misk.jdbc.StartVitessService
import misk.jdbc.TruncateTablesService
import misk.jdbc.VitessScaleSafetyChecks
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
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf(),
  val disableCrossShardQueryDetector: Boolean = false
) : KAbstractModule() {
  override fun configure() {
    val truncateTablesServiceKey = TruncateTablesService::class.toKey(qualifier)

    val startVitessServiceKey = StartVitessService::class.toKey(qualifier)
    val startVitessServiceProvider = getProvider(startVitessServiceKey)

    val crossShardQueryDetectorKey = VitessScaleSafetyChecks::class.toKey(qualifier)
    val crossShardQueryDetectorProvider = getProvider(crossShardQueryDetectorKey)

    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)

    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)

    multibind<Service>().to(startVitessServiceKey)

    bind(startVitessServiceKey).toProvider(Provider<StartVitessService> {
      StartVitessService(config = configProvider.get())
    }).asSingleton()

    val moshiProvider = getProvider(Moshi::class.java)
    val okHttpClientProvider = getProvider(OkHttpClient::class.java)
    bind(crossShardQueryDetectorKey).toProvider(Provider<VitessScaleSafetyChecks> {
      VitessScaleSafetyChecks(
          config = configProvider.get(),
          moshi = moshiProvider.get(),
          okHttpClient = okHttpClientProvider.get(),
          startVitessService = startVitessServiceProvider.get()
      )
    }).asSingleton()

    if (!disableCrossShardQueryDetector) {
      multibind<DataSourceDecorator>(qualifier).to(crossShardQueryDetectorKey)
    }

    multibind<Service>().to(truncateTablesServiceKey)

    bind(truncateTablesServiceKey).toProvider(Provider<TruncateTablesService> {
      TruncateTablesService(
          qualifier = qualifier,
          config = configProvider.get(),
          transacterProvider = transacterProvider,
          checks = crossShardQueryDetectorProvider.get(),
          startUpStatements = startUpStatements,
          shutDownStatements = shutDownStatements)
    }).asSingleton()
  }
}
