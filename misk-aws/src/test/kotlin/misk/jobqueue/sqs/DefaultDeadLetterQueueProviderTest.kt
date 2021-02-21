package misk.jobqueue.sqs

import misk.jobqueue.QueueName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DefaultDeadLetterQueueProviderTest {
  @Test fun `returns the main queue suffixed with _dlq`() {
    val provider = DefaultDeadLetterQueueProvider()

    assertThat(provider.deadLetterQueueFor(QueueName("test_queue")))
      .isEqualTo(QueueName("test_queue_dlq"))
    assertThat(provider.deadLetterQueueFor(QueueName("test_queue2")))
      .isEqualTo(QueueName("test_queue2_dlq"))
    assertThat(provider.deadLetterQueueFor(QueueName("test_queue_retryq")))
      .isEqualTo(QueueName("test_queue_dlq"))
    assertThat(provider.deadLetterQueueFor(QueueName("test_queue_dlq")))
      .isEqualTo(QueueName("test_queue_dlq"))
  }
}
