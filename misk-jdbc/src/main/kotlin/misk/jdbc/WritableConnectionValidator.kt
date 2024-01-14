package misk.jdbc

import java.sql.Connection
import com.mysql.cj.jdbc.ConnectionImpl as MysqlCjJdbcConnectionImpl
import com.mysql.cj.jdbc.JdbcConnection as MySQLJdbcConnection

class WritableConnectionValidator(
  private val connection: Connection
) : Connection by connection {

  override fun isValid(timeout: Int): Boolean {
    val valid = connection.isValid(timeout) && !isGloballyReadOnly()
    println("valid = $valid")
    return valid
  }

  private fun isGloballyReadOnly(): Boolean {
    if (!connection.isWrapperFor(MySQLJdbcConnection::class.java)) return false
    val mysqlJdbcConnection = connection.unwrap(MySQLJdbcConnection::class.java)

    if (mysqlJdbcConnection !is MysqlCjJdbcConnectionImpl) return false

    val readOnlyVars = listOf("@@global.read_only", "@@global.super_read_only")

    return readOnlyVars.any { variable ->
      val value = mysqlJdbcConnection.session.queryServerVariable(variable)?.toInt()
      println("value = $value")
      value != 0
    }
  }
}
