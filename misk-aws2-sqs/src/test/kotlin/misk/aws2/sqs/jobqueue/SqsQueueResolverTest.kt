package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import kotlin.test.assertEquals

@MiskTest(startService = true)
class SqsQueueResolverTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module = SqsJobQueueTestModule(dockerSqs)

  @Inject
  private lateinit var queueResolver: QueueResolver

  @Test
  @Disabled("Disabled test until docker authorization issues are resolvedc")
  fun `caches queue URL`() {
    val queueName = QueueName("test-queue")

    DockerSqs.client.createQueue(
      CreateQueueRequest.builder().queueName(queueName.value)
        .attributes(
          mapOf(
            QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20",
          ))
        .build()
    ).join()

    val result = queueResolver.getQueueUrl(queueName)
    queueResolver.getQueueUrl(queueName)

    assertEquals("test", result)
  }
}
