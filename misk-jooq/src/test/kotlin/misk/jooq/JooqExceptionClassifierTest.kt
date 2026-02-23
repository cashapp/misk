package misk.jooq

import java.sql.SQLException
import java.sql.SQLRecoverableException
import misk.jdbc.DataSourceType
import misk.jdbc.retry.RetryTransactionException
import org.assertj.core.api.Assertions.assertThat
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.Test

class JooqExceptionClassifierTest {

  @Test
  fun `DataAccessException is retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(DataAccessException("error"))).isTrue()
  }

  @Test
  fun `DataAccessException with SQL cause is retryable`() {
    val classifier = JooqExceptionClassifier()
    val cause = SQLException("connection error")
    assertThat(classifier.isRetryable(DataAccessException("error", cause))).isTrue()
  }

  @Test
  fun `inherits base classifier behavior for RetryTransactionException`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(RetryTransactionException())).isTrue()
  }

  @Test
  fun `inherits base classifier behavior for SQLRecoverableException`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(SQLRecoverableException())).isTrue()
  }

  @Test
  fun `generic RuntimeException is not retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(RuntimeException("error"))).isFalse()
  }

  @Test
  fun `wrapped DataAccessException is retryable`() {
    val classifier = JooqExceptionClassifier()
    val cause = DataAccessException("db error")
    val wrapper = RuntimeException("wrapper", cause)
    assertThat(classifier.isRetryable(wrapper)).isTrue()
  }

  @Test
  fun `inherits database-specific behavior from base classifier`() {
    val classifier = JooqExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isTrue()
  }

  @Test
  fun `Vitess exception not retryable without correct DataSourceType`() {
    val classifier = JooqExceptionClassifier(DataSourceType.MYSQL)
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isFalse()
  }
}
