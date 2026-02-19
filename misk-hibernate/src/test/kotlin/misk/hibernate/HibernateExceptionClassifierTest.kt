package misk.hibernate

import java.sql.SQLException
import java.sql.SQLRecoverableException
import javax.persistence.OptimisticLockException
import misk.jdbc.DataSourceType
import misk.jdbc.retry.RetryTransactionException
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.LockAcquisitionException
import org.junit.jupiter.api.Test

class HibernateExceptionClassifierTest {

  @Test
  fun `StaleObjectStateException is retryable`() {
    val classifier = HibernateExceptionClassifier()
    assertThat(classifier.isRetryable(StaleObjectStateException("Entity", "123"))).isTrue()
  }

  @Test
  fun `LockAcquisitionException is retryable`() {
    val classifier = HibernateExceptionClassifier()
    val sqlException = SQLException("Lock wait timeout")
    assertThat(classifier.isRetryable(LockAcquisitionException("Could not acquire lock", sqlException))).isTrue()
  }

  @Test
  fun `OptimisticLockException is retryable`() {
    val classifier = HibernateExceptionClassifier()
    assertThat(classifier.isRetryable(OptimisticLockException("Version mismatch"))).isTrue()
  }

  @Test
  fun `inherits base classifier behavior for RetryTransactionException`() {
    val classifier = HibernateExceptionClassifier()
    assertThat(classifier.isRetryable(RetryTransactionException())).isTrue()
  }

  @Test
  fun `inherits base classifier behavior for SQLRecoverableException`() {
    val classifier = HibernateExceptionClassifier()
    assertThat(classifier.isRetryable(SQLRecoverableException())).isTrue()
  }

  @Test
  fun `generic RuntimeException is not retryable`() {
    val classifier = HibernateExceptionClassifier()
    assertThat(classifier.isRetryable(RuntimeException("error"))).isFalse()
  }

  @Test
  fun `wrapped Hibernate exception is retryable`() {
    val classifier = HibernateExceptionClassifier()
    val cause = StaleObjectStateException("Entity", "123")
    val wrapper = RuntimeException("wrapper", cause)
    assertThat(classifier.isRetryable(wrapper)).isTrue()
  }

  @Test
  fun `inherits database-specific behavior from base classifier`() {
    val classifier = HibernateExceptionClassifier(DataSourceType.VITESS_MYSQL)
    val exception = SQLException(
      "vttablet: rpc error: code = Aborted desc = transaction 123: not found"
    )
    assertThat(classifier.isRetryable(exception)).isTrue()
  }
}
