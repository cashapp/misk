package misk.jdbc

import java.sql.Connection
import javax.sql.DataSource

internal class ConnectionDecoratingDataSource(
  private val connectionDecorator: (Connection) -> Connection,
  private val dataSource: DataSource,
) : DataSource by dataSource {
  override fun getConnection(): Connection {
    return connectionDecorator(dataSource.connection)
  }

  override fun getConnection(username: String?, password: String?): Connection {
    return connectionDecorator(dataSource.getConnection(username, password))
  }
}
