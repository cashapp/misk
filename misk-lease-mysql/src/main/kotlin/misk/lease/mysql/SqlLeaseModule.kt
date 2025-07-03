package misk.lease.mysql

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.google.inject.Provides
import com.google.inject.Provider
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.JdbcModule
import misk.lease.Leases
import wisp.lease.LeaseManager
import java.sql.Connection
import javax.sql.DataSource

@ExperimentalMiskApi
class SqlLeaseModule(
  private val config: DataSourceClustersConfig,
) : KAbstractModule() {
  override fun configure() {
    install(
      JdbcModule(
        LeaseDb::class,
        config.values.single().writer
      )
    )
    bind<LeaseManager>().to<SqlLeaseManager>()
  }

  @Provides
  @Singleton
  fun provideLeaseDatabase(
    @LeaseDb dataSource: Provider<DataSource>,
  ): LeaseDatabase {
    val driver = object : JdbcDriver() {
      override fun getConnection(): Connection {
        val connection = dataSource.get().connection
        // SQLDelight requires autoCommit = true by default for its transaction management to work
        connection.autoCommit = true
        return connection
      }

      override fun notifyListeners(vararg queryKeys: String) {
      }

      override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
      }

      override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
      }

      override fun closeConnection(connection: Connection) {
        connection.close()
      }
    }
    return LeaseDatabase(
      driver = driver,
      leasesAdapter = Leases.Adapter(
        held_untilAdapter = InstantAdapter,
      ),
    )
  }
}
