package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import misk.jobqueue.Job
import misk.jobqueue.JobHandler
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class SqsJobConsumerTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module =
      SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client)

  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var consumer: SqsJobConsumer

  private lateinit var queueNames: List<QueueName>
  private lateinit var handler: JobHandler
  private lateinit var handledJobs: CopyOnWriteArrayList<Job>

  @BeforeEach
  internal fun setUp() {
    handledJobs = CopyOnWriteArrayList()
    queueNames = (0..3).map { QueueName("sqs_job_queue_test$it") }

    queueNames.forEach { queueName ->
      val deadLetterQueueName = queueName.deadLetterQueue
      sqs.createQueue(deadLetterQueueName.value)
      sqs.createQueue(CreateQueueRequest()
          .withQueueName(queueName.value)
          .withAttributes(mapOf(
              // 1 second visibility timeout
              "VisibilityTimeout" to 1.toString())
          ))
    }

    handler = object : JobHandler {
      override fun handleJob(job: Job) {
        handledJobs.add(job)
        job.acknowledge()
      }
    }
  }

  @Test fun emptyQueuesReturnsFalse() {
    assertThat(consumer.QueueReceiver(queueNames, handler).runOnce()).isFalse()
    assertThat(handledJobs).isEmpty()
  }

  @Test fun jobsArePulledFromHigherPriorityQueuesFirst() {
    val receiver = consumer.QueueReceiver(queueNames, handler)

    enqueue(2, 0)
    enqueue(1, 1)
    enqueue(0, 2)

    assertThat(receiver.runOnce()).isTrue()
    assertThat(receiver.runOnce()).isTrue()
    assertThat(receiver.runOnce()).isTrue()
    assertThat(receiver.runOnce()).isFalse()

    assertThat(handledJobs.map { it.body }).containsExactly(
        "this is job 2",
        "this is job 1",
        "this is job 0"
    )
  }

  /**
   * Verifies that we only pull from low priority queues once before trying higher priority queues
   * again.
   */
  @Test fun higherPriorityQueuesAreRetried() {
    val receiver = consumer.QueueReceiver(queueNames, handler)

    // enqueue a job on high priority queue
    enqueue(0, 0)

    // enqueue 11 jobs on low priority queue (batch size is ten)
    (1..11).forEach { enqueue(1, it) }

    assertThat(receiver.runOnce()).isTrue()
    assertThat(receiver.runOnce()).isTrue()

    // enqueue another job on the high priority queue. this should be processed before the 3rd job.
    enqueue(0, 12)

    // process the high priority job
    assertThat(receiver.runOnce()).isTrue()
    // process the low priority job
    assertThat(receiver.runOnce()).isTrue()
    assertThat(receiver.runOnce()).isFalse()

    val jobNames = handledJobs.map { it.body }

    val name = { index:Int -> "this is job $index" }
    assertThat(jobNames.first()).isEqualTo(name(0))
    assertThat(jobNames.slice(1..10)).containsAll((1..10).map(name))
    assertThat(jobNames[11]).isEqualTo(name(12))
    assertThat(jobNames[12]).isEqualTo(name(11))
  }

  @Test fun doesNotBlockOnProcessing() {
    val countDownLatch = CountDownLatch(1)
    val countDownLatch2 = CountDownLatch(4)
    handler = object : JobHandler {
      override fun handleJob(job: Job) {
        assertThat(countDownLatch.await(100, TimeUnit.MILLISECONDS)).isTrue()
        handledJobs.add(job)
        job.acknowledge()
        countDownLatch2.countDown()
      }
    }
    val receiver = consumer.QueueReceiver(queueNames, handler)

    (0..3).forEach { enqueue(0, it) }
    assertThat(receiver.runOnce()).isTrue()
    assertThat(handledJobs).isEmpty()
    countDownLatch.countDown()
    assertThat(countDownLatch2.await(100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(handledJobs).hasSize(4)
  }

  @Test fun waitsForThreadToBeAvailable() {
    val startJobLatch = CountDownLatch(1)
    val fourJobsFinishedLatch = CountDownLatch(4)
    val allJobsFinishedLatch = CountDownLatch(5)
    val finishJobLatch = CountDownLatch(1)

    handler = object : JobHandler {
      override fun handleJob(job: Job) {
        assertThat(startJobLatch.await(10, TimeUnit.SECONDS)).isTrue()
        handledJobs.add(job)
        job.acknowledge()
        fourJobsFinishedLatch.countDown()
        allJobsFinishedLatch.countDown()
        assertThat(finishJobLatch.await(10, TimeUnit.SECONDS)).isTrue()
      }
    }
    val receiver = consumer.QueueReceiver(queueNames, handler)

    (0..4).forEach { enqueue(0, it) }
    assertThat(receiver.runOnce()).isTrue()
    assertThat(handledJobs).isEmpty()
    startJobLatch.countDown()
    assertThat(fourJobsFinishedLatch.await(100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(handledJobs).hasSize(4)
    finishJobLatch.countDown()
    assertThat(allJobsFinishedLatch.await(100, TimeUnit.MILLISECONDS)).isTrue()
    assertThat(handledJobs).hasSize(5)
  }

  private fun enqueue(queueId: Int, jobId: Int) {
    queue.enqueue(queueNames[queueId], "this is job $jobId",
        attributes = mapOf("index" to jobId.toString()))
  }
}
