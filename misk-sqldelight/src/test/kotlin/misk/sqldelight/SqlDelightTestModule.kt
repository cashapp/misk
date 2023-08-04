package misk.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.google.inject.Provides
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.JdbcModule
import misk.jdbc.JdbcTestingModule
import misk.logging.LogCollectorModule
import misk.sqldelight.testing.MoviesDatabase
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule
import wisp.deployment.TESTING
import java.sql.Connection
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.sql.DataSource

class SqlDelightTestModule() : KAbstractModule() {
  override fun configure() {
    install(LogCollectorModule())
    install(
      Modules.override(MiskTestingServiceModule()).with(
        FakeClockModule(),
        MockTracingBackendModule()
      )
    )
    install(DeploymentModule(TESTING))

    val config = MiskConfig.load<SqlDelightTestConfig>("sqldelighttestmodule", TESTING)

    install(JdbcModule(
      SqlDelightTestdb::class,
      config.data_source
    ))
    install(JdbcTestingModule(
      SqlDelightTestdb::class
    ))
  }

  @Provides
  @Singleton
  fun provideMoviesDatabase(
    @SqlDelightTestdb dataSource: Provider<DataSource>
  ): MoviesDatabase {
    val driver = object : JdbcDriver() {
      override fun getConnection(): Connection {
        val connection = dataSource.get().connection
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
    return MoviesDatabase(driver)
  }

  @Provides
  @Singleton
  @SqlDelightTestdb
  fun provideRetryingTransacter(moviesDatabase: MoviesDatabase): RetryingTransacter {
    return RetryingTransacter(moviesDatabase)
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class SqlDelightTestdb

