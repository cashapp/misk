package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import misk.jobqueue.Job
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.subscribe
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@MiskTest(startService = true)
internal class SqsJobQueueTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module =
      SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client)

  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var consumer: JobConsumer
  @Inject private lateinit var sqsMetrics: SqsMetrics
  @Inject @ForSqsConsumer lateinit var taskQueue: RepeatedTaskQueue

  private lateinit var queueName: QueueName
  private lateinit var deadLetterQueueName: QueueName

  @BeforeEach fun createQueues() {
    // Ensure that each test case runs on a unique queue
    queueName = QueueName("sqs_job_queue_test")
    deadLetterQueueName = queueName.deadLetterQueue
    sqs.createQueue(deadLetterQueueName.value)
    sqs.createQueue(CreateQueueRequest()
        .withQueueName(queueName.value)
        .withAttributes(mapOf(
            // 1 second visibility timeout
            "VisibilityTimeout" to 1.toString())
        ))
  }

  @Test fun enqueueAndHandle() {
    val handledJobs = CopyOnWriteArrayList<Job>()
    val allJobsComplete = CountDownLatch(10)
    consumer.subscribe(queueName) {
      handledJobs.add(it)
      it.acknowledge()
      allJobsComplete.countDown()
    }

    for (i in (0 until 10)) {
      queue.enqueue(queueName, "this is job $i", attributes = mapOf("index" to i.toString()))
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
    assertThat(sortedJobs.map { it.attributes }).containsExactly(
        mapOf("index" to "0"),
        mapOf("index" to "1"),
        mapOf("index" to "2"),
        mapOf("index" to "3"),
        mapOf("index" to "4"),
        mapOf("index" to "5"),
        mapOf("index" to "6"),
        mapOf("index" to "7"),
        mapOf("index" to "8"),
        mapOf("index" to "9")
    )

    // Confirm metrics
    assertThat(sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()).isEqualTo(10.0)
    assertThat(sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(10)

    assertThat(sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()).isEqualTo(10.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()).isEqualTo(10.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(10)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
  }

  @Test fun enqueueAndHandlePrioritized() {
    val handledJobs = CopyOnWriteArrayList<Job>()
    val allJobsComplete = CountDownLatch(3)

    val queueName2 = QueueName("sqs_job_queue_test2")
    val queueName3 = QueueName("sqs_job_queue_test3")

    // Create extra queues
    listOf(queueName2, queueName3).forEach { queueName ->
      val deadLetterQueueName = queueName.deadLetterQueue
      sqs.createQueue(deadLetterQueueName.value)
      sqs.createQueue(CreateQueueRequest()
          .withQueueName(queueName.value)
          .withAttributes(mapOf(
              // 1 second visibility timeout
              "VisibilityTimeout" to 1.toString())
          ))
    }

    val queues = listOf(queueName, queueName2, queueName3)
    // Create new queues
    queues.filter { it != queueName }.forEach { queueName ->
      val deadLetterQueueName = queueName.deadLetterQueue
      sqs.createQueue(deadLetterQueueName.value)
      sqs.createQueue(CreateQueueRequest()
          .withQueueName(queueName.value)
          .withAttributes(mapOf(
              // 1 second visibility timeout
              "VisibilityTimeout" to 1.toString())
          ))
    }
    // Set up handler on queuess
    queues.forEach { queueName ->
      consumer.subscribe(queueName) { job ->
        handledJobs.add(job)
        job.acknowledge()
        allJobsComplete.countDown()
      }
    }
    // Put the jobs on the queues in reverse order to verify that they still get processed in
    // priority order.
    queues.reversed().forEachIndexed { index, queueName ->
      queue.enqueue(queueName, "this is job $index",
          attributes = mapOf("index" to index.toString()))
    }

    // Process all events
    assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

    val sortedJobs = handledJobs.sortedBy { it.body }
    assertThat(sortedJobs.map { it.body }).containsExactly(
        "this is job 0",
        "this is job 1",
        "this is job 2"
    )
    assertThat(sortedJobs.map { it.attributes }).containsExactly(
        mapOf("index" to "0"),
        mapOf("index" to "1"),
        mapOf("index" to "2")
    )

    // Confirm metrics
    queues.forEach { queueName ->
      assertThat(sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()).isEqualTo(1.0)
      assertThat(sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
      assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(1)

      assertThat(sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()).isEqualTo(1.0)
      // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
      // messages in varying batches
      assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

      assertThat(sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()).isEqualTo(1.0)
      assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(1)

      assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
    }
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
    assertThat(sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()).isEqualTo(1.0)
    assertThat(sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()).isEqualTo(2.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()).isEqualTo(1.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
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
    assertThat(sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()).isEqualTo(2.0)
    assertThat(sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(2)

    assertThat(sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()).isEqualTo(2.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.jobsDeadLettered.labels(queueName.value, queueName.value).get()).isEqualTo(2.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(2)

    assertThat(sqsMetrics.jobsEnqueued.labels(deadLetterQueueName.value, deadLetterQueueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.jobEnqueueFailures.labels(deadLetterQueueName.value, deadLetterQueueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(deadLetterQueueName.value, deadLetterQueueName.value)).isEqualTo(0)

    assertThat(sqsMetrics.jobsReceived.labels(deadLetterQueueName.value, deadLetterQueueName.value).get()).isEqualTo(2.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(deadLetterQueueName.value, deadLetterQueueName.value)).isNotZero()

    assertThat(sqsMetrics.jobsAcknowledged.labels(deadLetterQueueName.value, deadLetterQueueName.value).get()).isEqualTo(2.0)
    assertThat(sqsMetrics.jobsDeadLettered.labels(deadLetterQueueName.value, deadLetterQueueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(deadLetterQueueName.value, deadLetterQueueName.value)).isEqualTo(2)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
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
    assertThat(sqsMetrics.jobsEnqueued.labels(queueName.value, queueName.value).get()).isEqualTo(1.0)
    assertThat(sqsMetrics.jobEnqueueFailures.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsSendTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(sqsMetrics.jobsReceived.labels(queueName.value, queueName.value).get()).isEqualTo(3.0)
    // Can't predict how many times we'll receive have since consumers may get 0 messages and retry, or may get many
    // messages in varying batches
    assertThat(sqsMetrics.sqsReceiveTime.count(queueName.value, queueName.value)).isNotZero()

    assertThat(sqsMetrics.jobsAcknowledged.labels(queueName.value, queueName.value).get()).isEqualTo(1.0)
    assertThat(sqsMetrics.jobsDeadLettered.labels(queueName.value, queueName.value).get()).isEqualTo(0.0)
    assertThat(sqsMetrics.sqsDeleteTime.count(queueName.value, queueName.value)).isEqualTo(1)

    assertThat(sqsMetrics.handlerFailures.labels(queueName.value, queueName.value).get()).isEqualTo(2.0)
  }

  @Test fun waitsForDispatchedTasksToFail() {
    turnOffTaskQueue()
    consumer.subscribe(queueName) {
      throw IllegalStateException("boom!")
    }
    queue.enqueue(queueName, "fail away")
    val receiver = (consumer as SqsJobConsumer).getReceiver(queueName)
    assertThat(receiver.runOnce()).isTrue()
  }

  @Test fun noWork() {
    turnOffTaskQueue()
    consumer.subscribe(queueName) {
      throw IllegalStateException("boom!")
    }
    val receiver = (consumer as SqsJobConsumer).getReceiver(queueName)
    assertThat(receiver.runOnce()).isFalse()
  }

  private fun turnOffTaskQueue() {
    taskQueue.stopAsync()
    taskQueue.awaitTerminated()
  }
}
