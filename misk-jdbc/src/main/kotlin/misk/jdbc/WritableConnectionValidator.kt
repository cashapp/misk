package misk.jdbc

import java.sql.Connection
import com.mysql.cj.jdbc.ConnectionImpl as MysqlJdbcConnectionImpl
import com.mysql.cj.jdbc.JdbcConnection as MySQLJdbcConnection

class WritableConnectionValidator(
  private val connection: Connection
) : Connection by connection {

  override fun isValid(timeout: Int): Boolean {
    return connection.isValid(timeout)
      && !isMySQLGloballyReadOnly()
  }

  /*
    We don't override isReadOnly() because checking for global read only might have some inadvertent side effects.
    Creating a special purpose function just for ourselves limits the blast radius.
   */
  private fun isMySQLGloballyReadOnly(): Boolean {
    if (!connection.isWrapperFor(MySQLJdbcConnection::class.java)) return false

    val mysqlJdbcConnection = connection.unwrap(MySQLJdbcConnection::class.java)
    if (mysqlJdbcConnection !is MysqlJdbcConnectionImpl) return false

    val readOnlyVars = listOf("@@global.read_only", "@@global.super_read_only")

    return readOnlyVars.any { variable ->
      mysqlJdbcConnection
        .session
        .queryServerVariable(variable)?.toInt() != 0
    }
  }
}
