package misk.jdbc

import com.mysql.cj.jdbc.ConnectionImpl as MysqlJdbcConnectionImpl
import com.mysql.cj.jdbc.JdbcConnection as MySQLJdbcConnection
import java.sql.Connection

internal class WritableConnectionValidator(private val connection: Connection) : Connection by connection {

  override fun isValid(timeout: Int): Boolean {
    return connection.isValid(timeout) &&
      !connection.isReadOnly &&
      !isAnyMySQLVarEnabled(MYSQL_GLOBAL_READ_ONLY_VARIABLES)
  }

  /*
   We don't override isReadOnly() because checking for additional read only server variables
   might have some inadvertent side effects for callers who don't expect such additional checks in isReadOnly().
   Creating a special purpose function just for ourselves limits the blast radius.
  */
  private fun isAnyMySQLVarEnabled(vars: List<String>): Boolean {
    if (!connection.isWrapperFor(MySQLJdbcConnection::class.java)) return false

    val mysqlJdbcConnection = connection.unwrap(MySQLJdbcConnection::class.java)
    if (mysqlJdbcConnection !is MysqlJdbcConnectionImpl) return false

    return vars.any { variable -> mysqlJdbcConnection.session.queryServerVariable(variable)?.toInt() != 0 }
  }

  companion object {
    private val MYSQL_GLOBAL_READ_ONLY_VARIABLES =
      listOf("@@global.innodb_read_only", "@@global.read_only", "@@global.super_read_only")
  }
}
