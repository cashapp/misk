package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueUrlRequest
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.jobqueue.QueueName
import misk.mockito.Mockito.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`

internal class QueueResolverTest {

  @Test
  fun `getDeadLetter returns global DLQ when one is configured`() {
    val queue = QueueName("barb-queue")
    val resolved = resolver(globalDlqName = "global_dlq").getDeadLetter(queue)

    assertThat(resolved.queueName).isEqualTo("global_dlq")
  }

  @Test
  fun `getDeadLetter returns DLQ named after main queue when global DLQ is not configured`() {
    val queue = QueueName("barb-queue")
    val resolved = resolver(globalDlqName = null).getDeadLetter(queue)

    assertThat(resolved.sqsQueueName).isEqualTo(queue.deadLetterQueue)
  }

  /** Create a test subject, optionally configured to use a global DLQ. */
  private fun resolver(globalDlqName: String?): QueueResolver {
    val currentRegion = AwsRegion("region")
    val sqs = mock<AmazonSQS>()
    `when`(sqs.getQueueUrl(any(GetQueueUrlRequest::class.java)))
        // Any bogus value works since tests check resolved queue name, not URL.
        .thenReturn(GetQueueUrlResult().withQueueUrl("http://bogus.info"))

    return QueueResolver(
        currentRegion = currentRegion,
        currentAccount = AwsAccountId("account_id"),
        defaultSQS = sqs,
        crossRegionSQS = mapOf(currentRegion to sqs),
        defaultForReceivingSQS = sqs,
        crossRegionForReceivingSQS = mapOf(currentRegion to sqs),
        externalQueues = mapOf(),
        config = AwsSqsJobQueueConfig(global_dead_letter_queue_name = globalDlqName))
  }
}
