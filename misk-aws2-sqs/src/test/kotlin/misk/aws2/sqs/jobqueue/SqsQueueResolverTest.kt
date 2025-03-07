package misk.aws2.sqs.jobqueue

import misk.jobqueue.QueueName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class SqsQueueResolverTest {
  private val client = mock<SqsAsyncClient>()
  private var queueResolver = QueueResolver(client)

  @BeforeEach
  fun setup() {
    whenever(client.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.supplyAsync {
      GetQueueUrlResponse.builder()
        .queueUrl("url://test-queue")
        .build()
    })
  }

  @Test
  fun `caches queue URL`() {
    val queueName = QueueName("test-queue")

    // Calling this twice should result in a single call to AWS
    val result = queueResolver.getQueueUrl(queueName)
    queueResolver.getQueueUrl(queueName)

    assertEquals("url://test-queue", result)

    verify(client).getQueueUrl(any<GetQueueUrlRequest>())
  }
}
