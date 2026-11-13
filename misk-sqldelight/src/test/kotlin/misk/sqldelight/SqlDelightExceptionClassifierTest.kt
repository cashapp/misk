package misk.sqldelight

import app.cash.sqldelight.db.OptimisticLockException
import java.sql.SQLException
import java.sql.SQLRecoverableException
import misk.jdbc.DataSourceType
import misk.jdbc.retry.RetryTransactionException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SqlDelightExceptionClassifierTest {

  @Test
  fun `OptimisticLockException is retryable`() {
    val classifier = SqlDelightExceptionClassifier()
    assertThat(classifier.isRetryable(OptimisticLockException("conflict"))).isTrue()
  }

  @Test
  fun `inherits base classifier behavior for RetryTransactionException`() {
    val classifier = SqlDelightExceptionClassifier()
    assertThat(classifier.isRetryable(RetryTransactionException())).isTrue()
  }

  @Test
  fun `inherits base classifier behavior for SQLRecoverableException`() {
    val classifier = SqlDelightExceptionClassifier()
    assertThat(classifier.isRetryable(SQLRecoverableException())).isTrue()
  }

  @Test
  fun `generic RuntimeException is not retryable`() {
    val classifier = SqlDelightExceptionClassifier()
    assertThat(classifier.isRetryable(RuntimeException("error"))).isFalse()
  }

  @Test
  fun `wrapped OptimisticLockException is retryable`() {
    val classifier = SqlDelightExceptionClassifier()
    val cause = OptimisticLockException("conflict")
    val wrapper = RuntimeException("wrapper", cause)
    assertThat(classifier.isRetryable(wrapper)).isTrue()
  }

  @Test
  fun `inherits database-specific behavior from base classifier`() {
    val classifier = SqlDelightExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isTrue()
  }

  @Test
  fun `Vitess exception not retryable without correct DataSourceType`() {
    val classifier = SqlDelightExceptionClassifier(DataSourceType.MYSQL)
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isFalse()
  }

  @Test
  fun `connection closed SQLException is retryable`() {
    val classifier = SqlDelightExceptionClassifier()
    assertThat(classifier.isRetryable(SQLException("Connection is closed"))).isTrue()
  }
}
