package misk.lease.mysql

import misk.annotation.ExperimentalMiskApi
import misk.inject.ReusableTestModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcTestingModule

/** Configures testing modules. */
@OptIn(ExperimentalMiskApi::class)
class SqlLeaseTestingModule(
  private val dbConfig: DataSourceClustersConfig = defaultDbConfig()
) : ReusableTestModule() {
  override fun configure() {
    bind<SqlLeaseConfig>().toInstance(SqlLeaseConfig(leaseDurationInSec = LEASE_DURATION_SECONDS))
    install(SqlLeaseModule(dbConfig))
    install(JdbcTestingModule<LeaseDb>())
  }
  companion object {
    val LEASE_DURATION_SECONDS = 60L

    fun defaultDbConfig(): DataSourceClustersConfig {
      return DataSourceClustersConfig(
        mapOf(
          "misk-lease-test-001" to DataSourceClusterConfig(
            writer = DataSourceConfig(
              type = DataSourceType.MYSQL,
              username = "root",
              password = "",
              database = "misk_lease_test_001",
              migrations_resource = "classpath:/migrations",
            ),
            reader = null,
          )
        )
      )
    }
  }
}
