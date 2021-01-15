package misk.hibernate

import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.JdbcTestingModule
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
@Deprecated("Use JdbcTestingModule instead")
class HibernateTestingModule(
  private val qualifier: KClass<out Annotation>,
  private val config: DataSourceConfig? = null,
  private val startUpStatements: List<String> = listOf(),
  private val shutDownStatements: List<String> = listOf(),
  // TODO: default to opt-out once these are ready for prime time.
  private val scaleSafetyChecks: Boolean = false
) : KAbstractModule() {
  override fun configure() {
    install(JdbcTestingModule(qualifier, startUpStatements, shutDownStatements, scaleSafetyChecks))
  }
}
