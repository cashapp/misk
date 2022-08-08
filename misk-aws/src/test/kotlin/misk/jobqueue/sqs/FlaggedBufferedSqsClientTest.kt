package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.DeleteMessageResult
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.amazonaws.services.sqs.model.SendMessageResult
import com.squareup.moshi.Moshi
import misk.feature.testing.FakeFeatureFlags
import misk.mockito.Mockito.mock
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions

internal class FlaggedBufferedSqsClientTest {
  @Test fun `dynamically switches between underlying clients based on feature status`() {
    val flags = FakeFeatureFlags(
      wisp.feature.testing.FakeFeatureFlags(
        Moshi.Builder().build()
      )
    )
    flags.override(FlaggedBufferedSqsClient.FEATURE, false)

    val unbuffered = mock<AmazonSQS>()
    val buffered = mock<AmazonSQS>()
    // stub the feature-flag gated methods for both clients
    listOf(unbuffered, buffered).forEach { client ->
      `when`(client.sendMessage(any())).thenReturn(SendMessageResult())
      `when`(client.sendMessage(anyString(), anyString())).thenReturn(SendMessageResult())
      `when`(client.deleteMessage(any())).thenReturn(DeleteMessageResult())
      `when`(client.deleteMessage(anyString(), anyString())).thenReturn(DeleteMessageResult())
    }

    val sendRequest = SendMessageRequest()
      .withQueueUrl("queueUrl")
      .withMessageBody("body")
    val deleteRequest = DeleteMessageRequest()
      .withQueueUrl("queueUrl")
      .withReceiptHandle("receipt")
    val client = FlaggedBufferedSqsClient(unbuffered, buffered, "test-app", flags)
    val executeRequests = {
      client.sendMessage(sendRequest)
      client.sendMessage(sendRequest.queueUrl, sendRequest.messageBody)
      client.deleteMessage(deleteRequest)
      client.deleteMessage(deleteRequest.queueUrl, deleteRequest.receiptHandle)
    }

    // default/flag off, use unbuffered client
    executeRequests()
    verifyNoInteractions(buffered)
    verify(unbuffered).sendMessage(sendRequest)
    verify(unbuffered).sendMessage(sendRequest.queueUrl, sendRequest.messageBody)
    verify(unbuffered).deleteMessage(deleteRequest)
    verify(unbuffered).deleteMessage(deleteRequest.queueUrl, deleteRequest.receiptHandle)

    // flip flag, confirm usage of buffered client
    flags.overrideKey(FlaggedBufferedSqsClient.FEATURE, "test-app", true)
    executeRequests()
    verifyNoMoreInteractions(unbuffered)
    verify(buffered).sendMessage(sendRequest)
    verify(buffered).sendMessage(sendRequest.queueUrl, sendRequest.messageBody)
    verify(buffered).deleteMessage(deleteRequest)
    verify(buffered).deleteMessage(deleteRequest.queueUrl, deleteRequest.receiptHandle)

    client.shutdown()
    verify(buffered).shutdown()
    verify(unbuffered).shutdown()
  }
}
