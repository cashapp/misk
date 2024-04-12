package misk.jobqueue.sqs

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import jakarta.inject.Inject
import misk.clustering.fake.lease.FakeLeaseManager
import misk.inject.KAbstractModule
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.SqsJobConsumer.Companion.CONSUMERS_BATCH_SIZE
import misk.jobqueue.sqs.TaggedLoggerJobQueueTest.SqsJobQueueTestTaggedLogger.Companion.getTaggedLogger
import misk.jobqueue.subscribe
import misk.logging.LogCollectorModule
import misk.logging.TaggedLogger
import misk.tasks.RepeatedTaskQueue
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.feature.testing.FakeFeatureFlags
import wisp.logging.LogCollector
import wisp.logging.Tag
import wisp.logging.getLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

@MiskTest(startService = true)
internal class TaggedLoggerJobQueueTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module = object: KAbstractModule() {
    override fun configure() {
      install(SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client))
      install(LogCollectorModule())
    }
  }

  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var consumer: SqsJobConsumer
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var sqsMetrics: SqsMetrics
  @Inject @ForSqsHandling lateinit var taskQueue: RepeatedTaskQueue
  @Inject private lateinit var fakeFeatureFlags: FakeFeatureFlags
  @Inject private lateinit var fakeLeaseManager: FakeLeaseManager
  @Inject private lateinit var queueResolver: QueueResolver

  private lateinit var queueName: QueueName
  private lateinit var deadLetterQueueName: QueueName

  @BeforeEach
  fun setUp() {
    queueName = QueueName("sqs_job_queue_test")
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

  @Test
  fun shouldLogMdcTagsWhenSqsJobConsumerLogsExceptionFromService() {
    val allJobsComplete = CountDownLatch(1)
    var messageIdToVerify: String? = null
    val jobsReceived = AtomicInteger()

    consumer.subscribe(queueName) {
      // If already received and processed job and thrown exception, now trigger the test verification
      if (jobsReceived.getAndIncrement() == 1) {
        it.acknowledge()
        allJobsComplete.countDown()
        return@subscribe
      }

      taggedLogger
        .testTag("test123")
        .asContext {
          messageIdToVerify = it.id
          taggedLogger.info("Test log with mdc")
          throw SqsJobQueueTestException("Test exception")
        }
    }

    queue.enqueue(queueName, "job body")

    assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

    val serviceLogEvents = logCollector.takeEvents(TaggedLoggerJobQueueTest::class, consumeUnmatchedLogs = false)
    val sqsLogErrorEvents = logCollector.takeEvents(SqsJobConsumer::class)
      .filter { it.level == Level.ERROR }

    assertThat(serviceLogEvents).hasSize(1)
    assertThat(serviceLogEvents.single().message).isEqualTo("Test log with mdc")
    assertThat(serviceLogEvents.single().mdcPropertyMap).containsEntry("testTag", "test123")

    assertThat(sqsLogErrorEvents).hasSize(1)
    assertThat(sqsLogErrorEvents.single().message).isEqualTo("error handling job from ${queueName.value}")
    assertThat(sqsLogErrorEvents.single().mdcPropertyMap).containsEntry("testTag", "test123")
    assertExistingMdcPropertiesArePresent(sqsLogErrorEvents.single(), messageIdToVerify)
  }

  @Test
  fun shouldLogNormallyWhenNotUsingTaggedLogger() {
    val allJobsComplete = CountDownLatch(1)
    var messageIdToVerify: String? = null
    val jobsReceived = AtomicInteger()

    consumer.subscribe(queueName) {
      // If already received and processed job and thrown exception, now trigger the test verification
      if (jobsReceived.getAndIncrement() == 1) {
        it.acknowledge()
        allJobsComplete.countDown()
        return@subscribe
      }

      messageIdToVerify = it.id
      normalLogger.info("Test log without mdc")
      throw SqsJobQueueTestException("Test exception")
    }

    queue.enqueue(queueName, "job body")

    assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

    val serviceLogEvents = logCollector.takeEvents(TaggedLoggerJobQueueTest::class, consumeUnmatchedLogs = false)
    val sqsLogErrorEvents = logCollector.takeEvents(SqsJobConsumer::class)
      .filter { it.level == Level.ERROR }

    assertThat(serviceLogEvents).hasSize(1)
    assertThat(serviceLogEvents.single().message).isEqualTo("Test log without mdc")

    assertThat(sqsLogErrorEvents).hasSize(1)
    assertThat(sqsLogErrorEvents.single().message).isEqualTo("error handling job from ${queueName.value}")
    assertExistingMdcPropertiesArePresent(sqsLogErrorEvents.single(), messageIdToVerify)
  }

  private fun assertExistingMdcPropertiesArePresent(logEvent: ILoggingEvent, messageIdToVerify: String?) {
    assertThat(logEvent.mdcPropertyMap).containsEntry("sqs_job_id", messageIdToVerify)
    assertThat(logEvent.mdcPropertyMap).containsEntry("misk.job_queue.job_id", messageIdToVerify)
    assertThat(logEvent.mdcPropertyMap).containsEntry("misk.job_queue.queue_name", queueName.value)
    assertThat(logEvent.mdcPropertyMap).containsEntry("misk.job_queue.queue_type", "aws-sqs")
  }

  class SqsJobQueueTestException(override val message: String): Exception()

  companion object {
    val taggedLogger = this::class.getTaggedLogger()
    val normalLogger = getLogger<TaggedLoggerJobQueueTest>()
  }

  class SqsJobQueueTestTaggedLogger<L: Any>(logClass: KClass<L>): TaggedLogger<L, SqsJobQueueTestTaggedLogger<L>>(logClass) {
    fun testTag(value: String) = tag(Tag("testTag", value))

    companion object {
      fun <T : Any> KClass<T>.getTaggedLogger() = SqsJobQueueTestTaggedLogger(this)
    }
  }
}
