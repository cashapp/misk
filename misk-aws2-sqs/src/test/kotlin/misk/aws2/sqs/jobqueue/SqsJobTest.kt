package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import misk.jobqueue.QueueName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName

internal class SqsJobTest {
  private val moshi = Moshi.Builder().build()
  private val testQueue = QueueName("test")

  @Test
  fun `idempotenceKey returns key from jobqueue metadata`() {
    val message =
      Message.builder()
        .messageId("id-0")
        .body("body-0")
        .messageAttributes(
          mapOf(
            "foo" to MessageAttributeValue.builder().dataType("String").stringValue("bar").build(),
            SqsJob.JOBQUEUE_METADATA_ATTR to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(
                  """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test",
                |  "${SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY}": "ik-0",
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID}": "oti-0"
                |}"""
                    .trimMargin()
                )
                .build(),
          )
        )
        .build()

    val job = SqsJob(testQueue, moshi, message, "queue-url", System.currentTimeMillis())

    assertThat(job.idempotenceKey).isEqualTo("ik-0")
    assertThat(job.attributes).containsEntry("foo", "bar").doesNotContainKey(SqsJob.JOBQUEUE_METADATA_ATTR)
  }

  @Test
  fun `attributes includes both message attributes and system attributes`() {
    val sentTimestamp = System.currentTimeMillis().toString()
    val message =
      Message.builder()
        .messageId("id-0")
        .body("body-0")
        .attributes(
          mapOf(
            MessageSystemAttributeName.SENT_TIMESTAMP to sentTimestamp,
            MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT to "1",
          )
        )
        .messageAttributes(
          mapOf(
            "customAttribute" to MessageAttributeValue.builder().dataType("String").stringValue("customValue").build(),
            SqsJob.JOBQUEUE_METADATA_ATTR to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(
                  """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test",
                |  "${SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY}": "ik-0",
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID}": "oti-0"
                |}"""
                    .trimMargin()
                )
                .build(),
          )
        )
        .build()

    val job = SqsJob(testQueue, moshi, message, "queue-url", System.currentTimeMillis())

    assertThat(job.idempotenceKey).isEqualTo("ik-0")
    assertThat(job.attributes)
      // Custom message attributes are included
      .containsEntry("customAttribute", "customValue")
      // System attributes are included with key converted to string
      .containsEntry("SentTimestamp", sentTimestamp)
      .containsEntry("ApproximateReceiveCount", "1")
      // JOBQUEUE_METADATA_ATTR is filtered out
      .doesNotContainKey(SqsJob.JOBQUEUE_METADATA_ATTR)
  }

  @Test
  fun `attributes with only system attributes and no custom attributes`() {
    val sentTimestamp = System.currentTimeMillis().toString()
    val message =
      Message.builder()
        .messageId("id-0")
        .body("body-0")
        .attributes(
          mapOf(
            MessageSystemAttributeName.SENT_TIMESTAMP to sentTimestamp,
            MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP to sentTimestamp,
            MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT to "2",
          )
        )
        .messageAttributes(
          mapOf(
            SqsJob.JOBQUEUE_METADATA_ATTR to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(
                  """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test",
                |  "${SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY}": "ik-0",
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID}": "oti-0"
                |}"""
                    .trimMargin()
                )
                .build()
          )
        )
        .build()

    val job = SqsJob(testQueue, moshi, message, "queue-url", System.currentTimeMillis())

    assertThat(job.attributes)
      .containsEntry("SentTimestamp", sentTimestamp)
      .containsEntry("ApproximateFirstReceiveTimestamp", sentTimestamp)
      .containsEntry("ApproximateReceiveCount", "2")
      .doesNotContainKey(SqsJob.JOBQUEUE_METADATA_ATTR)
      .hasSize(3)
  }

  @Test
  fun `attributes with multiple custom attributes and system attributes`() {
    val sentTimestamp = System.currentTimeMillis().toString()
    val message =
      Message.builder()
        .messageId("id-0")
        .body("body-0")
        .attributes(mapOf(MessageSystemAttributeName.SENT_TIMESTAMP to sentTimestamp))
        .messageAttributes(
          mapOf(
            "attr1" to MessageAttributeValue.builder().dataType("String").stringValue("value1").build(),
            "attr2" to MessageAttributeValue.builder().dataType("String").stringValue("value2").build(),
            "attr3" to MessageAttributeValue.builder().dataType("String").stringValue("value3").build(),
            SqsJob.JOBQUEUE_METADATA_ATTR to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(
                  """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test",
                |  "${SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY}": "ik-0",
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGINAL_TRACE_ID}": "oti-0"
                |}"""
                    .trimMargin()
                )
                .build(),
          )
        )
        .build()

    val job = SqsJob(testQueue, moshi, message, "queue-url", System.currentTimeMillis())

    assertThat(job.attributes)
      .containsEntry("attr1", "value1")
      .containsEntry("attr2", "value2")
      .containsEntry("attr3", "value3")
      .containsEntry("SentTimestamp", sentTimestamp)
      .doesNotContainKey(SqsJob.JOBQUEUE_METADATA_ATTR)
      .hasSize(4)
  }

  @Test
  fun `body and id are correctly extracted from message`() {
    val message =
      Message.builder()
        .messageId("test-message-id")
        .body("test-body-content")
        .messageAttributes(
          mapOf(
            SqsJob.JOBQUEUE_METADATA_ATTR to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(
                  """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test",
                |  "${SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY}": "ik-0"
                |}"""
                    .trimMargin()
                )
                .build()
          )
        )
        .build()

    val job = SqsJob(testQueue, moshi, message, "queue-url", System.currentTimeMillis())

    assertThat(job.id).isEqualTo("test-message-id")
    assertThat(job.body).isEqualTo("test-body-content")
    assertThat(job.queueName).isEqualTo(testQueue)
  }

  @Test
  fun `idempotenceKey throws error when metadata attribute is missing`() {
    val message = Message.builder().messageId("id-0").body("body-0").build()

    val job = SqsJob(testQueue, moshi, message, "queue-url", System.currentTimeMillis())

    assertThrows<IllegalStateException> { job.idempotenceKey }
  }

  @Test
  fun `idempotenceKey throws error when idempotence_key is missing from metadata`() {
    val message =
      Message.builder()
        .messageId("id-0")
        .body("body-0")
        .messageAttributes(
          mapOf(
            SqsJob.JOBQUEUE_METADATA_ATTR to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(
                  """{
                |  "${SqsJob.JOBQUEUE_METADATA_ORIGIN_QUEUE}": "test"
                |}"""
                    .trimMargin()
                )
                .build()
          )
        )
        .build()

    val job = SqsJob(testQueue, moshi, message, "queue-url", System.currentTimeMillis())

    val exception = assertThrows<IllegalStateException> { job.idempotenceKey }
    assertThat(exception.message).contains(SqsJob.JOBQUEUE_METADATA_IDEMPOTENCE_KEY)
  }
}
