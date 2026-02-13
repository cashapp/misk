package misk.jdbc

import misk.jdbc.retry.DefaultExceptionClassifier
import org.junit.jupiter.api.Test
import java.sql.SQLException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionRetryHandlerTest {

  @Test
  fun `connection closed exceptions are retryable for all database types`() {
    val mysqlClassifier = DefaultExceptionClassifier(DataSourceType.MYSQL)
    val vitessClassifier = DefaultExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val tidbClassifier = DefaultExceptionClassifier(DataSourceType.TIDB)
    val cockroachClassifier = DefaultExceptionClassifier(DataSourceType.COCKROACHDB)

    val connectionClosedException = SQLException("Connection is closed")

    assertTrue(mysqlClassifier.isRetryable(connectionClosedException))
    assertTrue(vitessClassifier.isRetryable(connectionClosedException))
    assertTrue(tidbClassifier.isRetryable(connectionClosedException))
    assertTrue(cockroachClassifier.isRetryable(connectionClosedException))
  }

  @Test
  fun `vitess transaction not found is only retryable for vitess`() {
    val mysqlClassifier = DefaultExceptionClassifier(DataSourceType.MYSQL)
    val vitessClassifier = DefaultExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val tidbClassifier = DefaultExceptionClassifier(DataSourceType.TIDB)
    val cockroachClassifier = DefaultExceptionClassifier(DataSourceType.COCKROACHDB)

    val vitessException = SQLException("vttablet: rpc error: code = Aborted desc = transaction 1572922696317821557: not found (CallerID: )")

    assertFalse(mysqlClassifier.isRetryable(vitessException))
    assertTrue(vitessClassifier.isRetryable(vitessException))
    assertFalse(tidbClassifier.isRetryable(vitessException))
    assertFalse(cockroachClassifier.isRetryable(vitessException))
  }

  @Test
  fun `cockroach restart transaction is only retryable for cockroach`() {
    val mysqlClassifier = DefaultExceptionClassifier(DataSourceType.MYSQL)
    val vitessClassifier = DefaultExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val tidbClassifier = DefaultExceptionClassifier(DataSourceType.TIDB)
    val cockroachClassifier = DefaultExceptionClassifier(DataSourceType.COCKROACHDB)

    val cockroachException = SQLException("restart transaction", "40001", 40001)

    assertFalse(mysqlClassifier.isRetryable(cockroachException))
    assertFalse(vitessClassifier.isRetryable(cockroachException))
    assertFalse(tidbClassifier.isRetryable(cockroachException))
    assertTrue(cockroachClassifier.isRetryable(cockroachException))
  }

  @Test
  fun `tidb write conflict is only retryable for tidb`() {
    val mysqlClassifier = DefaultExceptionClassifier(DataSourceType.MYSQL)
    val vitessClassifier = DefaultExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val tidbClassifier = DefaultExceptionClassifier(DataSourceType.TIDB)
    val cockroachClassifier = DefaultExceptionClassifier(DataSourceType.COCKROACHDB)

    val tidbException = SQLException("write conflict", "HY000", 9007)

    assertFalse(mysqlClassifier.isRetryable(tidbException))
    assertFalse(vitessClassifier.isRetryable(tidbException))
    assertTrue(tidbClassifier.isRetryable(tidbException))
    assertFalse(cockroachClassifier.isRetryable(tidbException))
  }

}
