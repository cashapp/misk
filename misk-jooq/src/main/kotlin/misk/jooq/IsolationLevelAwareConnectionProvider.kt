package misk.jooq

import java.sql.Connection
import org.jooq.ConnectionProvider
import org.jooq.impl.DataSourceConnectionProvider

class IsolationLevelAwareConnectionProvider(
  private val dataSourceConnectionProvider: DataSourceConnectionProvider,
  private val transacterOptions: JooqTransacter.TransacterOptions,
) : ConnectionProvider {
  override fun acquire(): Connection {
    return dataSourceConnectionProvider.acquire().apply {
      transactionIsolation = transacterOptions.isolationLevel.value
    }
  }

  override fun release(connection: Connection) {
    dataSourceConnectionProvider.release(connection)
  }
}
