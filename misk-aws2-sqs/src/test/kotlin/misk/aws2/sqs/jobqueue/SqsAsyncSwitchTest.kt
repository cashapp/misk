package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.inject.AsyncSwitch
import misk.inject.FakeSwitch
import misk.inject.KAbstractModule
import misk.inject.Switch
import misk.inject.asSingleton
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler
import misk.testing.ExternalDependency
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@MiskTest(startService = true)
class SqsAsyncSwitchTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskExternalDependency private val fakeQueueCreator = AsyncSwitchFakeQueueCreator(dockerSqs)

  @MiskTestModule private val module = Modules()

  @Inject private lateinit var jobEnqueuer: SqsJobEnqueuer
  @Inject private lateinit var handlers: Map<QueueName, JobHandler>
  @Inject private lateinit var fakeSwitch: FakeSwitch

  private class Modules : KAbstractModule() {
    override fun configure() {
      install(
        SqsJobHandlerTestModule(
          DockerSqs,
          SqsConfig(
            per_queue_overrides =
              mapOf(
                "external-test-queue" to
                  SqsQueueConfig(region = "us-west-2", account_id = "1234567890", install_retry_queue = false)
              )
          ),
        )
      )
      bind<FakeSwitch>().asSingleton()
      bind<Switch>().to<FakeSwitch>()
      bindOptionalBinding<AsyncSwitch>().to<FakeSwitch>()
      multibind<TestFixture>().to<FakeSwitch>()
    }
  }

  @BeforeEach
  fun setUp() {
    fakeSwitch.enabledKeys.add("sqs")
  }

  @Test
  fun `messages are handled when sqs is enabled`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler
    handler.resetCounter(1)

    jobEnqueuer.enqueue(
      queueName = queueName,
      body = "enabled_message",
      idempotencyKey = "enabled_key",
      deliveryDelay = Duration.ZERO,
      attributes = emptyMap(),
    )

    assertTrue(handler.counter.await(10, TimeUnit.SECONDS), "Message should be handled when sqs is enabled")
    assertEquals(1, handler.jobs.size)
  }

  @Test
  fun `messages are not handled when sqs is disabled`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler
    handler.resetCounter(1)

    fakeSwitch.enabledKeys.remove("sqs")

    jobEnqueuer.enqueue(
      queueName = queueName,
      body = "disabled_message",
      idempotencyKey = "disabled_key",
      deliveryDelay = Duration.ZERO,
      attributes = emptyMap(),
    )

    val handled = handler.counter.await(3, TimeUnit.SECONDS)
    assertEquals(false, handled, "Message should NOT be handled when sqs is disabled")
    assertEquals(0, handler.jobs.size)
  }

  @Test
  fun `messages are handled after re-enabling sqs`() = runTest {
    val queueName = QueueName("test-queue-1")
    val handler = handlers[queueName] as ExampleHandler
    handler.resetCounter(1)

    fakeSwitch.enabledKeys.remove("sqs")

    jobEnqueuer.enqueue(
      queueName = queueName,
      body = "reenable_message",
      idempotencyKey = "reenable_key",
      deliveryDelay = Duration.ZERO,
      attributes = emptyMap(),
    )

    val handledWhileDisabled = handler.counter.await(3, TimeUnit.SECONDS)
    assertEquals(false, handledWhileDisabled, "Message should NOT be handled while sqs is disabled")

    fakeSwitch.enabledKeys.add("sqs")

    assertTrue(handler.counter.await(10, TimeUnit.SECONDS), "Message should be handled after re-enabling sqs")
    assertEquals(1, handler.jobs.size)
  }
}

private class AsyncSwitchFakeQueueCreator(private val dockerSqs: DockerSqs) : ExternalDependency {
  private val queues = listOf("test-queue-1", "test-queue-1_retryq", "test-queue-1_dlq", "external-test-queue")

  override fun startup() {}

  override fun shutdown() {}

  override fun beforeEach() {
    queues.forEach {
      dockerSqs.client
        .createQueue(
          CreateQueueRequest.builder()
            .queueName(it)
            .attributes(
              mapOf(
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20",
                QueueAttributeName.VISIBILITY_TIMEOUT to "20",
              )
            )
            .build()
        )
        .join()
    }
  }

  override fun afterEach() {}
}
