@file:JvmName("VitessDialect")

package misk.hibernate

import org.hibernate.dialect.MySQL57Dialect
import org.hibernate.exception.spi.SQLExceptionConversionDelegate

class VitessDialect : MySQL57Dialect() {
  override fun buildSQLExceptionConversionDelegate(): SQLExceptionConversionDelegate {
    val superDelegate = super.buildSQLExceptionConversionDelegate()
    return SQLExceptionConversionDelegate { sqlException, message, sql ->
      val exceptionMessage = sqlException.message
      if (exceptionMessage != null && exceptionMessage.contains("multi-db transaction attempted")) {
        throw CrossShardTransactionException(message, sqlException)
      }

      return@SQLExceptionConversionDelegate superDelegate.convert(sqlException, message, sql)
    }
  }
}