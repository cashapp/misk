package misk.jdbc

import java.sql.Connection
import com.mysql.cj.jdbc.ConnectionImpl as MySQLConnectionImpl

class WritableConnectionValidator(
  private val connection: Connection
) : Connection by connection {
  override fun isValid(timeout: Int): Boolean {
    return connection.isValid(timeout) && !isReadOnly
  }

  override fun isReadOnly(): Boolean {
    if (connection.isReadOnly) return true

    if (connection is MySQLConnectionImpl) {
      // verify that this is what aurora sets during blue green
      val isGloballyReadOnly = connection.session.queryServerVariable("@@global.read_only")
      if (isGloballyReadOnly != null) {
        return isGloballyReadOnly.toInt() != 0
      }
    }

    return false
  }
}
