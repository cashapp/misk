package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import misk.clustering.fake.lease.FakeLeaseManager
import misk.feature.testing.FakeFeatureFlags
import misk.jobqueue.Job
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.SqsJobConsumer.Companion.CONSUMERS_BATCH_SIZE
import misk.jobqueue.sqs.SqsJobConsumer.Companion.CONSUMERS_PER_QUEUE
import misk.jobqueue.sqs.SqsJobConsumer.Companion.POD_CONSUMERS_PER_QUEUE
import misk.jobqueue.subscribe
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
internal class SqsJobQueueTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module =
    SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client)

  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var consumer: SqsJobConsumer
  @Inject private lateinit var sqsMetrics: SqsMetrics
  @Inject @ForSqsHandling lateinit var taskQueue: RepeatedTaskQueue
  @Inject private lateinit var fakeFeatureFlags: FakeFeatureFlags
  @Inject private lateinit var fakeLeaseManager: FakeLeaseManager

  private lateinit var queueName: QueueName
  private lateinit var deadLetterQueueName: QueueName

  @BeforeEach fun createQueues() {
    // Ensure that each test case runs on a unique queue
    queueName = QueueName("sqs_job_queue_test")
    deadLetterQueueName = queueName.deadLetterQueue
    sqs.createQueue(deadLetterQueueName.value)
    sqs.createQueue(
      CreateQueueRequest()
        .withQueueName(queueName.value)
        .withAttributes(
          mapOf(
            // 1 second visibility timeout
            "VisibilityTimeout" to 1.toString()
          )
        )
    )
    fakeFeatureFlags.override(CONSUMERS_BATCH_SIZE, 10)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun enqueueAndHandle(perPodConsumers: Boolean) {
    if (perPodConsumers) {
      enablePerPodConsumers()
    } else {
      enablePerQueueConsumers()
    }

    val handledJobs = CopyOnWriteArrayList<Job>()
    val allJobsComplete = CountDownLatch(10)
    consumer.subscribe(queueName) {
      handledJobs.add(it)
      it.acknowledge()
      allJobsComplete.countDown()
    }

    for (i in (0 until 10)) {
      queue.enqueue(
        queueName,
        "this is job $i",
        "ik-$i",
        attributes = mapOf("index" to i.toString())
      )
    }

    assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

    val sortedJobs = handledJobs.sortedBy { it.body }
    assertThat(sortedJobs.map { it.body }).containsExactly(
      "this is job 0",
      "this is job 1",
      "this is job 2",
      "this is job 3",
      "this is job 4",
      "this is job 5",
      "this is job 6",
      "this is job 7",
      "this is job 8",
      "this is job 9"
    )
    assertThat(sortedJobs.map { it.idempotenceKey }).containsExactly(
      "ik-0",
      "ik-1",
      "ik-2",
      "ik-3",
      "ik-4",
      "ik-5",
      "ik-6",
      "ik-7",
      "ik-8",
      "ik-9"
    )
    assertThat(sortedJobs.map { it.attributes["index"] }).containsExactly(
      "0",
      "1",
      "2",
      "3",
      "4",
      "5",
      "6",
      "7",
      "8",
      "9"
    )

    // Confirm metrics
    assertThat(sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()).isEqualTo(
      10.0
    )
    assertThat(
      sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()
    ).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(10)

    assertThat(sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()).isEqualTo(
      10.0
    )
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(
      sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()
    ).isEqualTo(10.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(10)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(
      0.0
    )
  }

  @Test fun retriesIfNotAcknowledged() {
    val handledJobs = CopyOnWriteArrayList<Job>()

    queue.enqueue(queueName, "this is my job")

    val jobsReceived = AtomicInteger()
    val allJobsCompleted = CountDownLatch(2)
    consumer.subscribe(queueName) {
      handledJobs.add(it)
      // Only acknowledge second attempt
      if (jobsReceived.getAndIncrement() == 1) it.acknowledge()
      allJobsCompleted.countDown()
    }

    assertThat(allJobsCompleted.await(10, TimeUnit.SECONDS)).isTrue()

    // Should have processed the same job twice
    val messageId = handledJobs[0].id
    assertThat(handledJobs.map { it.body }).containsExactly("this is my job", "this is my job")
    assertThat(handledJobs).allSatisfy { assertThat(it.id).isEqualTo(messageId) }

    // Confirm metrics
    assertThat(
      sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()
    ).isEqualTo(1.0)
    assertThat(
      sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()
    ).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(
      sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()
    ).isEqualTo(2.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(
      sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()
    ).isEqualTo(1.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(
      0.0
    )
  }

  @Test fun movesToDeadLetterQueueIfRequested() {
    val handledJobs = CopyOnWriteArrayList<Job>()
    val allJobsCompleted = CountDownLatch(2)
    consumer.subscribe(queueName) {
      handledJobs.add(it)
      it.deadLetter()
      allJobsCompleted.countDown()
    }

    val deadLetterJobs = CopyOnWriteArrayList<Job>()
    val allDeadLetterJobsCompleted = CountDownLatch(2)
    consumer.subscribe(deadLetterQueueName) {
      deadLetterJobs.add(it)
      it.acknowledge()
      allDeadLetterJobsCompleted.countDown()
    }

    queue.enqueue(queueName, "this is job 1")
    queue.enqueue(queueName, "this is job 2")

    assertThat(allJobsCompleted.await(10, TimeUnit.SECONDS)).isTrue()
    assertThat(handledJobs.sortedBy { it.body }.map { it.body }).containsExactly(
      "this is job 1",
      "this is job 2"
    )

    assertThat(allDeadLetterJobsCompleted.await(10, TimeUnit.SECONDS)).isTrue()
    assertThat(deadLetterJobs.sortedBy { it.body }.map { it.body }).containsExactly(
      "this is job 1",
      "this is job 2"
    )

    // Confirm metrics
    assertThat(
      sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()
    ).isEqualTo(2.0)
    assertThat(
      sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()
    ).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(2)

    assertThat(
      sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()
    ).isEqualTo(2.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(
      sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()
    ).isEqualTo(0.0)
    assertThat(
      sqsMetrics.jobsDeadLettered.labels(queueName.value, queueName.value).get()
    ).isEqualTo(2.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(2)

    assertThat(
      sqsMetrics.jobsEnqueued.labels(deadLetterQueueName.value, deadLetterQueueName.value).get()
    ).isEqualTo(0.0)
    assertThat(
      sqsMetrics.jobEnqueueFailures.labels(
        deadLetterQueueName.value,
        deadLetterQueueName.value
      ).get()
    ).isEqualTo(0.0)
    assertThat(
      sqsMetrics.sqsSendTime.count(
        deadLetterQueueName.value,
        deadLetterQueueName.value
      )
    ).isEqualTo(0)

    assertThat(
      sqsMetrics.jobsReceived.labels(deadLetterQueueName.value, deadLetterQueueName.value).get()
    ).isEqualTo(2.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(
      sqsMetrics.sqsReceiveTime.count(
        deadLetterQueueName.value,
        deadLetterQueueName.value
      )
    ).isNotZero()

    assertThat(
      sqsMetrics.jobsAcknowledged.labels(
        deadLetterQueueName.value,
        deadLetterQueueName.value
      ).get()
    ).isEqualTo(2.0)
    assertThat(
      sqsMetrics.jobsDeadLettered.labels(
        deadLetterQueueName.value,
        deadLetterQueueName.value
      ).get()
    ).isEqualTo(0.0)
    assertThat(
      sqsMetrics.sqsDeleteTime.count(
        deadLetterQueueName.value,
        deadLetterQueueName.value
      )
    ).isEqualTo(2)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(
      0.0
    )
  }

  @Test fun stopsDeliveryAfterClose() {
    val handledJobs = CopyOnWriteArrayList<Job>()
    consumer.subscribe(queueName) {
      handledJobs.add(it)
      it.acknowledge()
    }

    // Close the subscription and wait for any currently outstanding long-polls to complete
    turnOffTaskQueue()
    Thread.sleep(1001)

    // Send 10 jobs, then wait again for the long-poll to complete make sure none of them are delivered
    for (i in (0 until 10)) {
      queue.enqueue(queueName, "this is job $i", attributes = mapOf("index" to i.toString()))
    }

    Thread.sleep(1000)
    assertThat(handledJobs).isEmpty()
  }

  @Test fun gracefullyRecoversFromHandlerFailure() {
    val handledJobs = CopyOnWriteArrayList<Job>()
    val deliveryAttempts = AtomicInteger(0)
    val allJobsCompleted = CountDownLatch(1)
    consumer.subscribe(queueName) {
      check(deliveryAttempts.getAndIncrement() >= 2) {
        "this did not go well"
      }

      handledJobs.add(it)
      it.acknowledge()
      allJobsCompleted.countDown()
    }

    queue.enqueue(queueName, "keep failing away")

    assertThat(allJobsCompleted.await(10, TimeUnit.SECONDS)).isTrue()
    assertThat(handledJobs.map { it.body }).containsExactly("keep failing away")
    assertThat(deliveryAttempts.get()).isEqualTo(3)

    // Confirm metrics
    assertThat(
      sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()
    ).isEqualTo(1.0)
    assertThat(
      sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()
    ).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(
      sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()
    ).isEqualTo(3.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(
      sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()
    ).isEqualTo(1.0)
    assertThat(
      sqsMetrics.jobsDeadLettered.labels(queueName.value, queueName.value).get()
    ).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(
      2.0
    )
  }

  @Test fun waitsForDispatchedTasksToFail() {
    turnOffTaskQueue()
    consumer.subscribe(queueName) {
      throw IllegalStateException("boom!")
    }
    queue.enqueue(queueName, "fail away")
    val receiver = consumer.getReceiver(queueName)
    assertThat(receiver.run()).isEqualTo(Status.FAILED)
  }

  @Test fun noWork() {
    turnOffTaskQueue()
    consumer.subscribe(queueName) {
      throw IllegalStateException("boom!")
    }
    val receiver = consumer.getReceiver(queueName)
    assertThat(receiver.run()).isEqualTo(Status.NO_WORK)
  }

  @Test fun okIfAtLeastMessageWasConsumed() {
    turnOffTaskQueue()
    consumer.subscribe(queueName) {
      it.acknowledge()
    }
    queue.enqueue(queueName, "ok")
    val receiver = consumer.getReceiver(queueName)
    assertThat(receiver.run()).isEqualTo(Status.OK)
  }

  @Test fun worksIfDoesntHoldAnyLease() {
    fakeLeaseManager.markLeaseHeldElsewhere("sqs-job-consumer-sqs_job_queue_test-0")
    fakeLeaseManager.markLeaseHeldElsewhere("sqs-job-consumer-sqs_job_queue_test-1")
    fakeLeaseManager.markLeaseHeldElsewhere("sqs-job-consumer-sqs_job_queue_test-2")
    fakeLeaseManager.markLeaseHeldElsewhere("sqs-job-consumer-sqs_job_queue_test-3")
    fakeLeaseManager.markLeaseHeldElsewhere("sqs-job-consumer-sqs_job_queue_test-4")
    fakeLeaseManager.markLeaseHeldElsewhere("sqs-job-consumer-sqs_job_queue_test-5")
    turnOffTaskQueue()
    consumer.subscribe(queueName) {
      it.acknowledge()
    }
    queue.enqueue(queueName, "ok")
    val receiver = consumer.getReceiver(queueName)
    assertThat(receiver.run()).isEqualTo(Status.NO_WORK)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun batchEnqueueAndHandle(perPodConsumers: Boolean) {
    if (perPodConsumers) {
      enablePerPodConsumers()
    } else {
      enablePerQueueConsumers()
    }

    val handledJobs = CopyOnWriteArrayList<Job>()
    val allJobsComplete = CountDownLatch(10)
    consumer.subscribe(queueName) {
      handledJobs.add(it)
      it.acknowledge()
      allJobsComplete.countDown()
    }

    queue.batchEnqueue(
      queueName,
      (0 until 10).map { i ->
        JobQueue.JobRequest(
          idempotenceKey = "ik-$i",
          body = "this is job $i",
          deliveryDelay = Duration.ofMillis(1),
          attributes = mapOf("index" to i.toString())
        )
      }
    )

    assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

    val sortedJobs = handledJobs.sortedBy { it.body }

    //todo(hala): figure out how to test for failure scenario

    assertThat(sortedJobs.map { it.body }).containsExactly(
      "this is job 0",
      "this is job 1",
      "this is job 2",
      "this is job 3",
      "this is job 4",
      "this is job 5",
      "this is job 6",
      "this is job 7",
      "this is job 8",
      "this is job 9"
    )
    assertThat(sortedJobs.map { it.idempotenceKey }).containsExactly(
      "ik-0",
      "ik-1",
      "ik-2",
      "ik-3",
      "ik-4",
      "ik-5",
      "ik-6",
      "ik-7",
      "ik-8",
      "ik-9"
    )
    assertThat(sortedJobs.map { it.attributes["index"] }).containsExactly(
      "0",
      "1",
      "2",
      "3",
      "4",
      "5",
      "6",
      "7",
      "8",
      "9"
    )

    // Confirm metrics
    assertThat(sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()).isEqualTo(
      10.0
    )
    assertThat(
      sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()
    ).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()).isEqualTo(
      10.0
    )
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(
      sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()
    ).isEqualTo(10.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(10)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(
      0.0
    )
  }

  @Test
  fun batchEnqueueBatchLimit() {
    val handledJobs = CopyOnWriteArrayList<Job>()
    val allJobsComplete = CountDownLatch(10)
    consumer.subscribe(queueName) {
      handledJobs.add(it)
      it.acknowledge()
      allJobsComplete.countDown()
    }

    assertFailsWith<java.lang.IllegalStateException> {
      queue.batchEnqueue(
        queueName,
        (0 until 11).map { i ->
          JobQueue.JobRequest(
            idempotenceKey = "ik-$i",
            body = "this is job $i",
            deliveryDelay = Duration.ofMillis(1),
            attributes = mapOf("index" to i.toString())
          )
        }
      )
    }
  }

  private fun enablePerPodConsumers() {
    fakeFeatureFlags.override(CONSUMERS_PER_QUEUE, -1)
    fakeFeatureFlags.override(POD_CONSUMERS_PER_QUEUE, 5)
  }

  private fun enablePerQueueConsumers() {
    fakeFeatureFlags.override(CONSUMERS_PER_QUEUE, 5)
    fakeFeatureFlags.override(POD_CONSUMERS_PER_QUEUE, -1)
  }

  private fun turnOffTaskQueue() {
    taskQueue.stopAsync()
    taskQueue.awaitTerminated()
  }
}
