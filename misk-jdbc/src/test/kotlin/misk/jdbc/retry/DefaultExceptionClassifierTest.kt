package misk.jdbc.retry

import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import misk.jdbc.DataSourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultExceptionClassifierTest {

  @Test
  fun `RetryTransactionException is retryable`() {
    val classifier = DefaultExceptionClassifier()
    assertThat(classifier.isRetryable(RetryTransactionException())).isTrue()
    assertThat(classifier.isRetryable(RetryTransactionException("test"))).isTrue()
    assertThat(classifier.isRetryable(RetryTransactionException("test", RuntimeException()))).isTrue()
  }

  @Test
  fun `SQLRecoverableException is retryable`() {
    val classifier = DefaultExceptionClassifier()
    assertThat(classifier.isRetryable(SQLRecoverableException())).isTrue()
  }

  @Test
  fun `SQLTransientException is retryable`() {
    val classifier = DefaultExceptionClassifier()
    assertThat(classifier.isRetryable(SQLTransientException())).isTrue()
  }

  @Test
  fun `connection closed SQLException is retryable`() {
    val classifier = DefaultExceptionClassifier()
    assertThat(classifier.isRetryable(SQLException("Connection is closed"))).isTrue()
  }

  @Test
  fun `generic SQLException is not retryable`() {
    val classifier = DefaultExceptionClassifier()
    assertThat(classifier.isRetryable(SQLException("Some other error"))).isFalse()
  }

  @Test
  fun `generic RuntimeException is not retryable`() {
    val classifier = DefaultExceptionClassifier()
    assertThat(classifier.isRetryable(RuntimeException("error"))).isFalse()
  }

  @Test
  fun `exception with retryable cause is retryable`() {
    val classifier = DefaultExceptionClassifier()
    val cause = RetryTransactionException()
    val wrapper = RuntimeException("wrapper", cause)
    assertThat(classifier.isRetryable(wrapper)).isTrue()
  }

  @Test
  fun `nested retryable cause is found`() {
    val classifier = DefaultExceptionClassifier()
    val innerCause = SQLRecoverableException()
    val middleWrapper = RuntimeException("middle", innerCause)
    val outerWrapper = RuntimeException("outer", middleWrapper)
    assertThat(classifier.isRetryable(outerWrapper)).isTrue()
  }

  @Test
  fun `Vitess transaction not found is retryable when dataSourceType is VITESS_MYSQL`() {
    val classifier = DefaultExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isTrue()
  }

  @Test
  fun `Vitess transaction not found is not retryable when dataSourceType is MYSQL`() {
    val classifier = DefaultExceptionClassifier(DataSourceType.MYSQL)
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isFalse()
  }

  @Test
  fun `Vitess transaction not found is not retryable when dataSourceType is null`() {
    val classifier = DefaultExceptionClassifier()
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isFalse()
  }

  @Test
  fun `CockroachDB restart transaction is retryable when dataSourceType is COCKROACHDB`() {
    val classifier = DefaultExceptionClassifier(DataSourceType.COCKROACHDB)
    val exception = object : SQLException("restart transaction") {
      override fun getErrorCode() = 40001
    }
    assertThat(classifier.isRetryable(exception)).isTrue()
  }

  @Test
  fun `CockroachDB restart transaction is not retryable when dataSourceType is MYSQL`() {
    val classifier = DefaultExceptionClassifier(DataSourceType.MYSQL)
    val exception = object : SQLException("restart transaction") {
      override fun getErrorCode() = 40001
    }
    assertThat(classifier.isRetryable(exception)).isFalse()
  }

  @Test
  fun `TiDB write conflict is retryable when dataSourceType is TIDB`() {
    val classifier = DefaultExceptionClassifier(DataSourceType.TIDB)
    val exception = object : SQLException("write conflict") {
      override fun getErrorCode() = 9007
    }
    assertThat(classifier.isRetryable(exception)).isTrue()
  }

  @Test
  fun `TiDB write conflict is not retryable when dataSourceType is MYSQL`() {
    val classifier = DefaultExceptionClassifier(DataSourceType.MYSQL)
    val exception = object : SQLException("write conflict") {
      override fun getErrorCode() = 9007
    }
    assertThat(classifier.isRetryable(exception)).isFalse()
  }
}
