package misk.hibernate.vitess

import misk.vitess.CowriteException
import org.hibernate.cfg.Environment
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.MySQL8Dialect
import org.hibernate.dialect.function.NoArgSQLFunction
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.GenericJDBCException
import org.hibernate.exception.spi.SQLExceptionConversionDelegate
import org.hibernate.type.StandardBasicTypes
import java.sql.SQLException
import java.util.Optional

class VitessDialect : MySQL8Dialect() {
  private val vitessShardExceptionParser
    : VitessShardExceptionParser = VitessShardExceptionParser()

  init {
    // Statement batching is not implemented yet
    getDefaultProperties().setProperty(
      Environment.STATEMENT_BATCH_SIZE,
      Dialect.NO_BATCH
    )

    registerKeyword("virtual")
    registerKeyword("status")
    registerFunction(
      "current_timestamp",
      NoArgSQLFunction("current_timestamp", StandardBasicTypes.TIMESTAMP)
    )
  }

  override fun useInputStreamToInsertBlob(): Boolean {
    return false
  }

  override fun buildSQLExceptionConversionDelegate(): SQLExceptionConversionDelegate {
    return SQLExceptionConversionDelegate { sqlException: SQLException, message: String, sql: String? ->
      val exceptionMessage = sqlException.message
      if (exceptionMessage != null && exceptionMessage.contains("Duplicate entry")) {
        return@SQLExceptionConversionDelegate ConstraintViolationException(
          message,
          sqlException,
          sql,
          null
        )
      } else if (exceptionMessage != null && exceptionMessage.contains("multi-db transaction attempted")) {
        throw CowriteException(message, sqlException)
      } else if (VitessExceptionDetector.isWaiterPoolExhausted(sqlException)) {
        return@SQLExceptionConversionDelegate PoolWaiterCountExhaustedException(sqlException)
      } else {
        val vitessShardException: Optional<VitessShardExceptionData> =
          vitessShardExceptionParser.parseShardInfo(sqlException)
        if (vitessShardException.isPresent()) {
          return@SQLExceptionConversionDelegate VitessShardException(vitessShardException.get())
        }
        return@SQLExceptionConversionDelegate GenericJDBCException(
          sqlException.message,
          sqlException,
          sql
        )
      }
    }
  }
}

