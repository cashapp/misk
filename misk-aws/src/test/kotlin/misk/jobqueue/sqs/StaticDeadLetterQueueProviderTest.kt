package misk.jobqueue.sqs

import misk.jobqueue.QueueName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StaticDeadLetterQueueProviderTest {
  @Test fun `always returns the same DLQ name`() {
    val staticDlqName = "constant"
    val provider = StaticDeadLetterQueueProvider(staticDlqName)

    assertThat(provider.deadLetterQueueFor(QueueName("queue_1")))
      .isEqualTo(QueueName(staticDlqName))
    assertThat(provider.deadLetterQueueFor(QueueName("queue_2")))
      .isEqualTo(QueueName(staticDlqName))
  }
}
