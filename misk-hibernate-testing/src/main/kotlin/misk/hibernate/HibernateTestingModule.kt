package misk.hibernate

import com.google.common.util.concurrent.Service
import com.google.inject.Inject
import com.squareup.moshi.Moshi
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
  private val shutDownStatements: List<String> = listOf(),
  val disableCrossShardQueryDetector: Boolean = false
) : KAbstractModule() {
  override fun configure() {
    val truncateTablesServiceKey = TruncateTablesService::class.toKey(qualifier)

    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)

    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)

    val useVitessChecks = ((config == null || config.type == DataSourceType.VITESS)
        && !disableCrossShardQueryDetector)
    if (useVitessChecks) {
      bindVitessChecks()
    }

    multibind<Service>().to(truncateTablesServiceKey)
    bind(truncateTablesServiceKey).toProvider(object : Provider<TruncateTablesService> {
      @Inject(optional = true) var vitessScaleSafetyChecks: VitessScaleSafetyChecks? = null

      override fun get(): TruncateTablesService = TruncateTablesService(
        qualifier = qualifier,
        config = configProvider.get(),
        transacterProvider = transacterProvider,
        vitessScaleSafetyChecks = if (useVitessChecks) vitessScaleSafetyChecks else null,
        startUpStatements = startUpStatements,
        shutDownStatements = shutDownStatements
      )
    }).asSingleton()
  }

  private fun bindVitessChecks() {
    val startVitessServiceKey = StartVitessService::class.toKey(qualifier)
    val startVitessServiceProvider = getProvider(startVitessServiceKey)

    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)

    val crossShardQueryDetectorKey = VitessScaleSafetyChecks::class.toKey(qualifier)

    val moshiProvider = getProvider(Moshi::class.java)

    bind(crossShardQueryDetectorKey).toProvider(Provider<VitessScaleSafetyChecks> {
      VitessScaleSafetyChecks(
        config = configProvider.get(),
        moshi = moshiProvider.get(),
        okHttpClient = OkHttpClient(),
        startVitessService = startVitessServiceProvider.get()
      )
    }).asSingleton()

    multibind<DataSourceDecorator>(qualifier).to(crossShardQueryDetectorKey)
  }
}
