package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
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
  private val shutDownStatements: List<String> = listOf()
) : KAbstractModule() {
  override fun configure() {
    val truncateTablesServiceKey = TruncateTablesService::class.toKey(qualifier)

    val configKey = DataSourceConfig::class.toKey(qualifier)
    val configProvider = getProvider(configKey)

    val transacterKey = Transacter::class.toKey(qualifier)
    val transacterProvider = getProvider(transacterKey)

    multibind<Service>().to(truncateTablesServiceKey)

    bind(truncateTablesServiceKey).toProvider(Provider<TruncateTablesService> {
      TruncateTablesService(
          qualifier = qualifier,
          config = configProvider.get(),
          transacterProvider = transacterProvider,
          startUpStatements = startUpStatements,
          shutDownStatements = shutDownStatements)
    })
  }
}
