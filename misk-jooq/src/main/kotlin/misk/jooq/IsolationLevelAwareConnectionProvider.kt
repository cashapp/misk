package misk.jooq

import org.jooq.ConnectionProvider
import org.jooq.impl.DataSourceConnectionProvider
import java.sql.Connection

class IsolationLevelAwareConnectionProvider(
  private val dataSourceConnectionProvider: DataSourceConnectionProvider,
  private val transacterOptions: JooqTransacter.TransacterOptions
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
