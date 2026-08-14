package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.Service
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import jakarta.inject.Inject
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.delay
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.inject.AsyncSwitch
import misk.jobqueue.QueueName
import misk.jobqueue.v2.Job
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@MiskTest(startService = true)
class SqsJobConsumerDrainTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module = SqsJobQueueTestModule(dockerSqs)

  @Inject private lateinit var sqsClientFactory: SqsClientFactory
  @Inject private lateinit var sqsQueueResolver: SqsQueueResolver
  @Inject private lateinit var visibilityTimeoutCalculator: VisibilityTimeoutCalculator
  @Inject private lateinit var moshi: Moshi
  @Inject private lateinit var dlqProvider: DeadLetterQueueProvider
  @Inject private lateinit var sqsMetrics: SqsMetrics
  @Inject private lateinit var clock: Clock
  @Inject private lateinit var tracer: Tracer
  @Inject private lateinit var asyncSwitch: AsyncSwitch

  private val consumers = mutableListOf<SqsJobConsumer>()

  @AfterEach
  fun stopConsumers() {
    consumers.forEach { consumer ->
      try {
        consumer.stopAsync().awaitTerminated(30, SECONDS)
      } catch (e: Exception) {
        // Best effort: reset() cancels any scopes a failed stop left behind.
      }
      consumer.reset()
    }
  }

  @Test
  fun `in-flight job finishes and acknowledges during drain and the message is never redelivered`() {
    val queueName = QueueName("drain-test-graceful")
    val queue = createQueue(queueName)
    val consumer = newConsumer()
    val handler = HoldingHandler()

    consumer.subscribe(queueName, handler, drainingConfig(drain_timeout_ms = 10_000))
    sendMessage(queue.queueUrl, "message")
    assertTrue(handler.started.await(10, SECONDS), "handler did not start")

    // The handler is holding the only in-flight job, so shutdown must not complete yet.
    consumer.stopAsync()
    Thread.sleep(500)
    assertNotEquals(Service.State.TERMINATED, consumer.state(), "stop must wait for the in-flight job")

    handler.release()
    consumer.awaitTerminated(10, SECONDS)

    // The job was acknowledged during the drain: nothing visible, and crucially nothing stuck in the
    // visibility window waiting to be redelivered or dead-lettered.
    val (visible, notVisible) = queueDepths(queue.queueUrl)
    assertEquals(0, visible, "message should have been deleted, not left visible")
    assertEquals(0, notVisible, "message should have been deleted, not abandoned in the visibility window")
  }

  @Test
  fun `handler exceeding the drain deadline is cancelled and shutdown terminates predictably`() {
    val queueName = QueueName("drain-test-timeout")
    val queue = createQueue(queueName)
    val consumer = newConsumer()
    val started = CountDownLatch(1)
    val handler =
      object : SuspendingJobHandler {
        override suspend fun handleJob(job: Job): JobStatus {
          started.countDown()
          delay(60_000)
          return JobStatus.OK
        }
      }

    // Visibility of 30s: an abandoned message stays in the not-visible window for the whole test.
    consumer.subscribe(queueName, handler, drainingConfig(drain_timeout_ms = 1_000, visibility_timeout = 30))
    sendMessage(queue.queueUrl, "message")
    assertTrue(started.await(10, SECONDS), "handler did not start")

    val stopStart = System.nanoTime()
    consumer.stopAsync()
    consumer.awaitTerminated(10, SECONDS)
    val stopMillis = (System.nanoTime() - stopStart) / 1_000_000

    assertTrue(stopMillis >= 1_000, "shutdown returned before the drain deadline: ${stopMillis}ms")
    assertTrue(stopMillis < 8_000, "shutdown did not terminate promptly after the drain deadline: ${stopMillis}ms")

    // The job was cancelled, so its message remains un-acknowledged in the visibility window.
    val (visible, notVisible) = queueDepths(queue.queueUrl)
    assertEquals(0, visible)
    assertEquals(1, notVisible, "cancelled job's message should remain in the visibility window")
  }

  @Test
  fun `drain stops new receives and leaves no message invisibly stranded`() {
    val queueName = QueueName("drain-test-no-new-receives")
    val queue = createQueue(queueName)
    val consumer = newConsumer()
    val handler = HoldingHandler()

    // One message at a time, rendezvous channel, single handler: the handler holds job 1 while the rest of the
    // pipeline backs up behind it. The exact split is scheduling-dependent: the merge over (queue, retryQueue)
    // decouples the receive loop from the rendezvous handoff, and the drain deliberately lets an in-flight
    // receive complete, so the poller may legitimately prefetch the third message before or during the drain.
    // The assertions below therefore check the drain invariants that hold under every legal scheduling.
    consumer.subscribe(
      queueName,
      handler,
      drainingConfig(
        drain_timeout_ms = 10_000,
        visibility_timeout = 30,
        parallelism = 1,
        concurrency = 1,
        channel_capacity = 0,
        max_number_of_messages = 1,
      ),
    )
    repeat(3) { sendMessage(queue.queueUrl, "message-$it") }
    assertTrue(handler.started.await(10, SECONDS), "handler did not start")
    // Stage a genuinely backed-up pipeline before draining: at least messages 1 and 2 received (not visible)
    // and the depths stable, i.e. the poller is parked in a handoff or an empty long poll.
    awaitStableQueueDepths(queue.queueUrl, minNotVisible = 2)

    consumer.stopAsync()
    handler.release()
    consumer.awaitTerminated(10, SECONDS)

    val handled = handler.handled.get()
    val (visible, notVisible) = queueDepths(queue.queueUrl)
    // THE invariant this feature exists for: a message in the not-visible window would be exactly the orphaned
    // in-flight delivery a drain must prevent.
    assertEquals(0, notVisible, "no message may be left stranded in the visibility window")
    // Conservation: every message was either fully handled-and-deleted, or never consumed and still visible
    // for the replacement pod.
    assertEquals(3, handled + visible, "every message must be handled or still visible, got handled=$handled")
    assertTrue(handled >= 2, "the messages received before the drain must have been handled, got $handled")

    // Polling provably stopped: a message still visible stays visible. 3s is well past the 1s receive
    // wait_timeout, so a poller that survived shutdown would have consumed it by now.
    Thread.sleep(3_000)
    assertEquals(Pair(visible, 0), queueDepths(queue.queueUrl), "polling must not continue after the drain")
  }

  @Test
  fun `queue without a drain timeout keeps the legacy immediate cancellation`() {
    val queueName = QueueName("drain-test-legacy")
    val queue = createQueue(queueName)
    val consumer = newConsumer()
    val handler = HoldingHandler()

    consumer.subscribe(queueName, handler, drainingConfig(drain_timeout_ms = null, visibility_timeout = 30))
    sendMessage(queue.queueUrl, "message")
    assertTrue(handler.started.await(10, SECONDS), "handler did not start")

    val stopStart = System.nanoTime()
    consumer.stopAsync()
    consumer.awaitTerminated(10, SECONDS)
    val stopMillis = (System.nanoTime() - stopStart) / 1_000_000
    assertTrue(stopMillis < 5_000, "legacy shutdown should cancel immediately, took ${stopMillis}ms")

    // Legacy behavior: the in-flight job is cancelled and its message stays in the visibility window.
    val (visible, notVisible) = queueDepths(queue.queueUrl)
    assertEquals(0, visible)
    assertEquals(1, notVisible)
  }

  @Test
  fun `mixed subscriptions drain configured queues and immediately cancel the rest`() {
    val drainingQueueName = QueueName("drain-test-mixed-draining")
    val legacyQueueName = QueueName("drain-test-mixed-legacy")
    val drainingQueue = createQueue(drainingQueueName)
    val legacyQueue = createQueue(legacyQueueName)
    val consumer = newConsumer()
    val drainingHandler = HoldingHandler()
    val legacyHandler = HoldingHandler()

    consumer.subscribe(
      drainingQueueName,
      drainingHandler,
      drainingConfig(drain_timeout_ms = 10_000, visibility_timeout = 30),
    )
    consumer.subscribe(legacyQueueName, legacyHandler, drainingConfig(drain_timeout_ms = null, visibility_timeout = 30))
    sendMessage(drainingQueue.queueUrl, "message")
    sendMessage(legacyQueue.queueUrl, "message")
    assertTrue(drainingHandler.started.await(10, SECONDS), "draining handler did not start")
    assertTrue(legacyHandler.started.await(10, SECONDS), "legacy handler did not start")

    consumer.stopAsync()
    drainingHandler.release()
    consumer.awaitTerminated(10, SECONDS)

    val (drainingVisible, drainingNotVisible) = queueDepths(drainingQueue.queueUrl)
    assertEquals(0, drainingVisible)
    assertEquals(0, drainingNotVisible, "drained queue's message should have been acknowledged")

    val (legacyVisible, legacyNotVisible) = queueDepths(legacyQueue.queueUrl)
    assertEquals(0, legacyVisible)
    assertEquals(1, legacyNotVisible, "legacy queue's job should have been cancelled without acknowledgement")
  }

  @Test
  fun `drain with no in-flight work terminates promptly without waiting for the deadline`() {
    val queueName = QueueName("drain-test-idle")
    val queue = createQueue(queueName)
    val consumer = newConsumer()
    val handled = CountDownLatch(1)
    val handler =
      object : SuspendingJobHandler {
        override suspend fun handleJob(job: Job): JobStatus {
          handled.countDown()
          return JobStatus.OK
        }
      }

    consumer.subscribe(queueName, handler, drainingConfig(drain_timeout_ms = 30_000))
    sendMessage(queue.queueUrl, "message")
    assertTrue(handled.await(10, SECONDS), "message was not handled")

    val stopStart = System.nanoTime()
    consumer.stopAsync()
    consumer.awaitTerminated(10, SECONDS)
    val stopMillis = (System.nanoTime() - stopStart) / 1_000_000

    assertTrue(stopMillis < 8_000, "idle drain should not wait for the 30s deadline, took ${stopMillis}ms")
  }

  @Test
  fun `stop after unsubscribe terminates promptly instead of waiting for the drain deadline`() {
    val queueName = QueueName("drain-test-unsubscribed")
    val queue = createQueue(queueName)
    val consumer = newConsumer()
    val handled = CountDownLatch(1)
    val handler =
      object : SuspendingJobHandler {
        override suspend fun handleJob(job: Job): JobStatus {
          handled.countDown()
          return JobStatus.OK
        }
      }

    consumer.subscribe(queueName, handler, drainingConfig(drain_timeout_ms = 30_000))
    sendMessage(queue.queueUrl, "message")
    assertTrue(handled.await(10, SECONDS), "message was not handled")

    // Unsubscribing cancels the handling scope; a later stop has nothing to drain for this queue and
    // must not burn the 30s drain deadline waiting on it.
    consumer.unsubscribe(queueName)

    val stopStart = System.nanoTime()
    consumer.stopAsync()
    consumer.awaitTerminated(10, SECONDS)
    val stopMillis = (System.nanoTime() - stopStart) / 1_000_000

    assertTrue(
      stopMillis < 8_000,
      "stop after unsubscribe should not wait for the drain deadline, took ${stopMillis}ms",
    )
  }

  @Test
  fun `subscribing after shutdown began is rejected instead of creating an undrainable subscription`() {
    val consumer = newConsumer()
    consumer.stopAsync().awaitTerminated(10, SECONDS)

    assertFailsWith<IllegalStateException> {
      consumer.subscribe(QueueName("drain-test-late"), HoldingHandler(), drainingConfig(drain_timeout_ms = null))
    }
  }

  /** Holds the first job until [release] is called; any further jobs complete immediately. */
  private class HoldingHandler : SuspendingJobHandler {
    val started = CountDownLatch(1)

    /** Jobs that ran to completion. A job cancelled mid-hold leaves its message visible and is not counted. */
    val handled = AtomicInteger(0)
    private val starts = AtomicInteger(0)
    private val release = CountDownLatch(1)

    override suspend fun handleJob(job: Job): JobStatus {
      val first = starts.getAndIncrement() == 0
      if (first) {
        started.countDown()
        while (release.count > 0) {
          delay(25)
        }
      }
      handled.incrementAndGet()
      return JobStatus.OK
    }

    fun release() {
      release.countDown()
    }
  }

  /**
   * A short wait_timeout keeps the tests fast and deterministic: the drain lets an in-flight receive complete, so with
   * the queue-default 20s long poll every drain would wait out the poll instead.
   */
  private fun drainingConfig(
    drain_timeout_ms: Long?,
    visibility_timeout: Int? = null,
    parallelism: Int = 1,
    concurrency: Int = 1,
    channel_capacity: Int = 0,
    max_number_of_messages: Int = 10,
  ) =
    SqsQueueConfig(
      region = "us-west-2",
      wait_timeout = 1,
      drain_timeout_ms = drain_timeout_ms,
      visibility_timeout = visibility_timeout,
      parallelism = parallelism,
      concurrency = concurrency,
      channel_capacity = channel_capacity,
      max_number_of_messages = max_number_of_messages,
    )

  private fun newConsumer(): SqsJobConsumer {
    val consumer =
      SqsJobConsumer(
        sqsClientFactory = sqsClientFactory,
        sqsQueueResolver = sqsQueueResolver,
        visibilityTimeoutCalculator = visibilityTimeoutCalculator,
        moshi = moshi,
        dlqProvider = dlqProvider,
        sqsMetrics = sqsMetrics,
        clock = clock,
        tracer = tracer,
        asyncSwitch = asyncSwitch,
      )
    consumer.startAsync().awaitRunning()
    consumers += consumer
    return consumer
  }

  /**
   * Waits until the poll pipeline has saturated: at least [minNotVisible] messages received (in the not-visible window)
   * and the depths unchanged across several consecutive samples. The exact visible/not-visible split is
   * scheduling-dependent (the poller may prefetch beyond the rendezvous handoff), so callers must not assume one.
   */
  private fun awaitStableQueueDepths(queueUrl: String, minNotVisible: Int) {
    val deadline = System.nanoTime() + SECONDS.toNanos(10)
    var stableSamples = 0
    var last = queueDepths(queueUrl)
    while (stableSamples < 5) {
      if (System.nanoTime() > deadline) fail("queue depths never saturated and settled, last=$last")
      Thread.sleep(100)
      val current = queueDepths(queueUrl)
      stableSamples = if (current == last && current.second >= minNotVisible) stableSamples + 1 else 0
      last = current
    }
  }

  /** Returns (visible, notVisible) message counts. ElasticMQ reports these attributes exactly. */
  private fun queueDepths(queueUrl: String): Pair<Int, Int> {
    val attributes =
      DockerSqs.client
        .getQueueAttributes(
          GetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributeNames(
              QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
              QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
            )
            .build()
        )
        .join()
        .attributes()
    return Pair(
      attributes[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]!!.toInt(),
      attributes[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE]!!.toInt(),
    )
  }

  private fun createQueue(queueName: QueueName): CreatedQueues {
    val result =
      DockerSqs.client
        .createQueue(
          CreateQueueRequest.builder()
            .queueName(queueName.value)
            .attributes(mapOf(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20"))
            .build()
        )
        .join()
    val retryResult =
      DockerSqs.client
        .createQueue(
          CreateQueueRequest.builder()
            .queueName("${queueName.value}_retryq")
            .attributes(mapOf(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20"))
            .build()
        )
        .join()
    val dlqResult =
      DockerSqs.client
        .createQueue(
          CreateQueueRequest.builder()
            .queueName("${queueName.value}_dlq")
            .attributes(mapOf(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS to "20"))
            .build()
        )
        .join()
    return CreatedQueues(result.queueUrl(), retryResult.queueUrl(), dlqResult.queueUrl())
  }

  private fun sendMessage(queueUrl: String, message: String) {
    DockerSqs.client.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build()).join()
  }

  data class CreatedQueues(val queueUrl: String, val retryQueueUrl: String, val dlqQueueUrl: String)
}
