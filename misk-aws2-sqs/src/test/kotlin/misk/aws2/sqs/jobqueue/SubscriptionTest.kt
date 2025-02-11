package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import misk.testing.ExternalDependency
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.time.Duration
import kotlin.test.assertEquals

@MiskTest(startService = true)
class SubscriptionTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  // creates queues before the tests start
  @MiskExternalDependency private val fakeQueueCreator = FakeQueueCreator(dockerSqs)
  @MiskTestModule private val module = SqsJobHandlerTestModule(dockerSqs)

  @Inject
  private lateinit var jobEnqueuer: SqsJobEnqueuer

  @Inject
  private lateinit var handlers: Map<QueueName, JobHandler>

  @Test
  fun `everything that is published via different APIs is consumed`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler

    jobEnqueuer.enqueue(
      queueName = queueName,
      body = "message_1",
      idempotencyKey = "idempotency_key_1",
      deliveryDelay = Duration.ofMillis(100),
      attributes = emptyMap(),
    )

    jobEnqueuer.enqueueBlocking(
      queueName = queueName,
      body = "message_2",
      idempotencyKey = "idempotency_key_2",
      deliveryDelay = Duration.ZERO,
      attributes = emptyMap()
    )

    jobEnqueuer.enqueueAsync(
      queueName = queueName,
      body = "message_3",
      idempotencyKey = "idempotency_key_3",
      deliveryDelay = Duration.ofSeconds(1),
      attributes = emptyMap()
    ).join()

    val latch = handler.counter
    latch.await()
    assertEquals(0, latch.count)

    val jobs = handler.jobs
    assertEquals(3, jobs.size)
  }
}

private class FakeQueueCreator(private val dockerSqs: DockerSqs) : ExternalDependency {
  private val queues = listOf("test-queue-1", "test-queue-1_retryq", "test-queue-1_dlq")

  override fun startup() {
    queues.forEach {
      DockerSqs.client.createQueue(
        CreateQueueRequest.builder().queueName(it)
          .attributes(
            mapOf(
              QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20",
              QueueAttributeName.VISIBILITY_TIMEOUT to "1",
            )
          )
          .build()
      ).join()
    }
  }

  override fun shutdown() {
  }

  override fun beforeEach() {

  }

  override fun afterEach() {
  }
}
