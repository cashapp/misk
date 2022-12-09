package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.squareup.moshi.Moshi
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.time.FakeClock
import javax.inject.Inject

@MiskTest(startService = true)
internal class SqsJobTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module =
    SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client)

  @Inject lateinit var sqs: AmazonSQS
  @Inject lateinit var queueResolver: QueueResolver
  @Inject lateinit var sqsMetrics: SqsMetrics
  @Inject lateinit var moshi: Moshi
  @Inject lateinit var clock: FakeClock

  private val testQueue = QueueName("test")

  @BeforeEach fun createQueues() {
    sqs.createQueue(
      CreateQueueRequest()
        .withQueueName(testQueue.value)
        .withAttributes(mapOf("VisibilityTimeout" to "1"))
    )
  }

  @Test
  fun getIdempotenceKey_withJobqueueMetadata() {
    val message = Message().apply {
      messageId = "id-0"
      body = "body-0"
      addMessageAttributesEntry(
        "foo",
        MessageAttributeValue().withDataType("String").withStringValue("bar")
      )
      addMessageAttributesEntry(
        SqsJob.JOBQUEUE_METADATA_ATTR,
        MessageAttributeValue().withDataType("String").withStringValue(
          """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test",
                |  "${SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY}": "ik-0",
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID}": "oti-0"
                |}""".trimMargin()
        )
      )
    }
    val job = SqsJob(QueueName("test"), queueResolver, sqsMetrics, moshi, message)

    assertThat(job.idempotenceKey).isEqualTo("ik-0")
    assertThat(job.attributes)
      .containsEntry("foo", "bar")
      .doesNotContainKey(SqsJob.JOBQUEUE_METADATA_ATTR)
  }

  @Test
  fun attributesAndMessageAttributesInJobAttributes() {
    val message = Message().apply {
      messageId = "id-0"
      body = "body-0"
      addAttributesEntry("SentTimestamp", clock.instant().toEpochMilli().toString())
      addMessageAttributesEntry(
        "foo",
        MessageAttributeValue().withDataType("String").withStringValue("bar")
      )
      addMessageAttributesEntry(
        SqsJob.JOBQUEUE_METADATA_ATTR,
        MessageAttributeValue().withDataType("String").withStringValue(
          """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test",
                |  "${SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY}": "ik-0",
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID}": "oti-0"
                |}""".trimMargin()
        )
      )
    }
    val job = SqsJob(QueueName("test"), queueResolver, sqsMetrics, moshi, message)

    assertThat(job.idempotenceKey).isEqualTo("ik-0")
    assertThat(job.attributes)
      .containsEntry("foo", "bar")
      .containsEntry("SentTimestamp", clock.instant().toEpochMilli().toString())
      .doesNotContainKey(SqsJob.JOBQUEUE_METADATA_ATTR)
  }
}
