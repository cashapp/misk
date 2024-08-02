package misk.jobqueue.sqs

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.SqsJobConsumer.Companion.CONSUMERS_BATCH_SIZE
import misk.jobqueue.subscribe
import misk.logging.LogCollectorModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import mu.KLogger
import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import wisp.feature.testing.FakeFeatureFlags
import wisp.logging.LogCollector
import wisp.logging.Tag
import wisp.logging.getLogger
import wisp.logging.withSmartTags
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

@MiskTest(startService = true)
internal class SmartTagsJobQueueTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module = object : KAbstractModule() {
    override fun configure() {
      install(SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client))
      install(LogCollectorModule())
    }
  }

  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var consumer: SqsJobConsumer
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var fakeFeatureFlags: FakeFeatureFlags

  private lateinit var queueName: QueueName

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

  @Deprecated("This nesting should be removed once TaggedLogger is removed")
  @Nested
  inner class OriginalTaggedLoggerTestsConverted {
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

      val serviceLogEvents =
        logCollector.takeEvents(SmartTagsJobQueueTest::class, consumeUnmatchedLogs = false)
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
        logger.info("Test log without mdc")
        throw SqsJobQueueTestException("Test exception")
      }

      queue.enqueue(queueName, "job body")

      assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

      val serviceLogEvents =
        logCollector.takeEvents(SmartTagsJobQueueTest::class, consumeUnmatchedLogs = false)
      val sqsLogErrorEvents = logCollector.takeEvents(SqsJobConsumer::class)
        .filter { it.level == Level.ERROR }

      assertThat(serviceLogEvents).hasSize(1)
      assertThat(serviceLogEvents.single().message).isEqualTo("Test log without mdc")

      assertThat(sqsLogErrorEvents).hasSize(1)
      assertThat(sqsLogErrorEvents.single().message).isEqualTo("error handling job from ${queueName.value}")
      assertExistingMdcPropertiesArePresent(sqsLogErrorEvents.single(), messageIdToVerify)
    }

    private fun assertExistingMdcPropertiesArePresent(
      logEvent: ILoggingEvent,
      messageIdToVerify: String?
    ) {
      assertThat(logEvent.mdcPropertyMap).containsEntry("sqs_job_id", messageIdToVerify)
      assertThat(logEvent.mdcPropertyMap).containsEntry("misk.job_queue.job_id", messageIdToVerify)
      assertThat(logEvent.mdcPropertyMap).containsEntry(
        "misk.job_queue.queue_name",
        queueName.value
      )
      assertThat(logEvent.mdcPropertyMap).containsEntry("misk.job_queue.queue_type", "aws-sqs")
    }
  }

  @Nested
  inner class WithSmartTagsTests {
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

        withSmartTags("testTag" to "test123") {
          messageIdToVerify = it.id
          logger.info("Test log with mdc")
          throw SqsJobQueueTestException("Test exception")
        }
      }

      queue.enqueue(queueName, "job body")

      assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

      val serviceLogEvents =
        logCollector.takeEvents(SmartTagsJobQueueTest::class, consumeUnmatchedLogs = false)
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
        logger.info("Test log without mdc")
        throw SqsJobQueueTestException("Test exception")
      }

      queue.enqueue(queueName, "job body")

      assertThat(allJobsComplete.await(10, TimeUnit.SECONDS)).isTrue()

      val serviceLogEvents =
        logCollector.takeEvents(SmartTagsJobQueueTest::class, consumeUnmatchedLogs = false)
      val sqsLogErrorEvents = logCollector.takeEvents(SqsJobConsumer::class)
        .filter { it.level == Level.ERROR }

      assertThat(serviceLogEvents).hasSize(1)
      assertThat(serviceLogEvents.single().message).isEqualTo("Test log without mdc")

      assertThat(sqsLogErrorEvents).hasSize(1)
      assertThat(sqsLogErrorEvents.single().message).isEqualTo("error handling job from ${queueName.value}")
      assertExistingMdcPropertiesArePresent(sqsLogErrorEvents.single(), messageIdToVerify)
    }

    private fun assertExistingMdcPropertiesArePresent(
      logEvent: ILoggingEvent,
      messageIdToVerify: String?
    ) {
      assertThat(logEvent.mdcPropertyMap).containsEntry("sqs_job_id", messageIdToVerify)
      assertThat(logEvent.mdcPropertyMap).containsEntry("misk.job_queue.job_id", messageIdToVerify)
      assertThat(logEvent.mdcPropertyMap).containsEntry(
        "misk.job_queue.queue_name",
        queueName.value
      )
      assertThat(logEvent.mdcPropertyMap).containsEntry("misk.job_queue.queue_type", "aws-sqs")
    }
  }

  class SqsJobQueueTestException(override val message: String) : Exception()

  companion object {
    val taggedLogger = this::class.getTaggedLogger()
    val logger = getLogger<SmartTagsJobQueueTest>()
  }
}

/**
 * This is an example wrapper to demonstrate how a service using an existing `TaggedLogger` implementation
 * could go about migrating to this new tagged logger with minimal changes in their service initially.
 *
 * In particular, this enables the tests above to have minimal migration from how they were originally
 * written to be able to thoroughly test the new `withSmartTags` style of logging.
 */
data class TestTaggedLogger(
  val kLogger: KLogger,
  private val tags: Set<Tag> = emptySet()
) : KLogger by kLogger {
  fun testTag(value: String) = tag("testTag" to value)
  fun testTagNested(value: String) = tag("testTagNested" to value)

  fun tag(vararg newTags: Tag) = TestTaggedLogger(kLogger, tags.plus(newTags))

  // Adds the tags to the Mapped Diagnostic Context for the current thread for the duration of the
  // block.
  fun <T> asContext(f: () -> T): T {
    return withSmartTags(*tags.toTypedArray()) {
      f()
    }
  }
}

fun <T : Any> KClass<T>.getTaggedLogger(): TestTaggedLogger = when {
  this.isCompanion -> {
    TestTaggedLogger(KotlinLogging.logger(this.java.declaringClass.canonicalName))
  }

  else -> {
    TestTaggedLogger(KotlinLogging.logger(this.java.canonicalName))
  }
}
