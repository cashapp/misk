package misk.jooq

import java.sql.SQLException
import java.sql.SQLRecoverableException
import misk.jdbc.DataSourceType
import misk.jdbc.retry.RetryTransactionException
import org.assertj.core.api.Assertions.assertThat
import org.jooq.exception.DataAccessException
import org.jooq.exception.DataChangedException
import org.jooq.exception.DataTypeException
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.exception.MappingException
import org.jooq.exception.NoDataFoundException
import org.jooq.exception.TooManyRowsException
import org.junit.jupiter.api.Test

class JooqExceptionClassifierTest {

  @Test
  fun `DataChangedException is retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(DataChangedException("optimistic lock conflict"))).isTrue()
  }

  @Test
  fun `DataChangedException wrapped in RuntimeException is retryable via cause-walking`() {
    val classifier = JooqExceptionClassifier()
    val wrapper = RuntimeException("wrapper", DataChangedException("optimistic lock conflict"))
    assertThat(classifier.isRetryable(wrapper)).isTrue()
  }

  @Test
  fun `bare DataAccessException is not retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(DataAccessException("error"))).isFalse()
  }

  @Test
  fun `DataAccessException wrapping a retryable SQL cause is retryable`() {
    val classifier = JooqExceptionClassifier()
    val cause = SQLRecoverableException("connection broken")
    assertThat(classifier.isRetryable(DataAccessException("error", cause))).isTrue()
  }

  @Test
  fun `DataAccessException wrapping a non-retryable SQL cause is not retryable`() {
    val classifier = JooqExceptionClassifier()
    val cause = SQLException("syntax error")
    assertThat(classifier.isRetryable(DataAccessException("error", cause))).isFalse()
  }

  @Test
  fun `IntegrityConstraintViolationException is not retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(IntegrityConstraintViolationException("duplicate key"))).isFalse()
  }

  @Test
  fun `NoDataFoundException is not retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(NoDataFoundException("no row"))).isFalse()
  }

  @Test
  fun `TooManyRowsException is not retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(TooManyRowsException("multiple rows"))).isFalse()
  }

  @Test
  fun `DataTypeException is not retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(DataTypeException("type conversion"))).isFalse()
  }

  @Test
  fun `MappingException is not retryable`() {
    val classifier = JooqExceptionClassifier()
    assertThat(classifier.isRetryable(MappingException("mapping"))).isFalse()
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

  @Test
  fun `DataAccessException wrapping a Vitess SQL cause is retryable for VITESS_MYSQL`() {
    val classifier = JooqExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val cause = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(DataAccessException("error", cause))).isTrue()
  }
}
