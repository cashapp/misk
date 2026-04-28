package misk.aws2.sqs.jobqueue.leased

import kotlin.test.assertEquals
import misk.aws2.sqs.jobqueue.DefaultDeadLetterQueueProvider
import misk.aws2.sqs.jobqueue.parentQueue
import misk.aws2.sqs.jobqueue.retryQueue
import misk.jobqueue.QueueName
import org.junit.jupiter.api.Test

class QueueNameExtensionsTest {
  @Test
  fun retryAndParentQueueNamesMatchV1() {
    val queue = QueueName("payments")

    assertEquals(QueueName("payments_retryq"), queue.retryQueue)
    assertEquals(QueueName("payments_retryq"), queue.retryQueue.retryQueue)
    assertEquals(queue, queue.retryQueue.parentQueue)
  }

  @Test
  fun deadLetterAndParentQueueNamesMatchV1() {
    val queue = QueueName("payments")

    assertEquals(QueueName("payments_dlq"), DefaultDeadLetterQueueProvider().deadLetterQueueFor(queue))
  }
}
