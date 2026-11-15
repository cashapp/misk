package misk.aws2.sqs.jobqueue

import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse

class SqsQueueResolverTest {
  private val client = mock<SqsAsyncClient>()
  private val sqsClientFactory = mock<SqsClientFactory>()
  private var sqsQueueResolver =
    SqsQueueResolver(
      sqsClientFactory,
      SqsConfig(
        all_queues = SqsQueueConfig(region = "us-west-2"),
        per_queue_overrides = mapOf("external-test-queue" to SqsQueueConfig(account_id = "12345", region = "us-east-1")),
      ),
    )

  @BeforeEach
  fun setup() {
    whenever(client.getQueueUrl(any<GetQueueUrlRequest>()))
      .thenReturn(CompletableFuture.supplyAsync { GetQueueUrlResponse.builder().queueUrl("url://test-queue").build() })

    whenever(sqsClientFactory.get("us-west-2")).thenReturn(client)
    whenever(sqsClientFactory.get("us-east-1")).thenReturn(client)
  }

  @Test
  fun `caches queue URL`() {
    val queueName = QueueName("test-queue")

    // Calling this twice should result in a single call to AWS
    val result = sqsQueueResolver.getQueueUrl(queueName)
    sqsQueueResolver.getQueueUrl(queueName)

    assertEquals("url://test-queue", result)

    verify(client).getQueueUrl(any<GetQueueUrlRequest>())
  }

  @Test
  fun `passed account id`() {
    val queueName = QueueName("external-test-queue")
    val result = sqsQueueResolver.getQueueUrl(queueName)

    assertEquals("url://test-queue", result)

    verify(client)
      .getQueueUrl(
        GetQueueUrlRequest.builder().queueName("external-test-queue").queueOwnerAWSAccountId("12345").build()
      )

    verify(sqsClientFactory).get("us-east-1")
  }
}
