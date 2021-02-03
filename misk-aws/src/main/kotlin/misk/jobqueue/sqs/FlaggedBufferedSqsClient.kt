package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.DeleteMessageResult
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.amazonaws.services.sqs.model.SendMessageResult
import misk.feature.Feature
import misk.feature.FeatureFlags

/**
 * Temporary shim for buffered and unbuffered [AmazonSQS] implementations, for feature-flagged rollout of buffered
 * SQS operations across cash cloud apps.
 *
 * Flag allows gates functionality on a per-service basis.
 *
 * Calls to [sendMessage] and [deleteMessage] will be routed to either buffered or unbuffered clients depending on the
 * state of the feature flag for this service. All other operations are delegated to the unbufferd implementation.
 *
 * Once usage of the buffered client is proven safe, this shim should be deleted and substituted with
 * [AmazonSQSBufferedAsyncClient].
 *
 * Flag: https://app.launchdarkly.com/cash/production/features/jobqueue-buffered-sqs-client/targeting
 */
class FlaggedBufferedSqsClient(
  private val unbufferedSqs : AmazonSQS,
  private val bufferedSqs : AmazonSQS,
  private val appName: String,
  private val featureFlags: FeatureFlags
) : AmazonSQS by unbufferedSqs {
  override fun sendMessage(sendMessageRequest: SendMessageRequest): SendMessageResult {
    return client().sendMessage(sendMessageRequest)
  }

  override fun sendMessage(queueUrl: String, messageBody: String): SendMessageResult {
    return client().sendMessage(queueUrl, messageBody)
  }

  override fun deleteMessage(deleteMessageRequest: DeleteMessageRequest): DeleteMessageResult {
    return client().deleteMessage(deleteMessageRequest)
  }

  override fun deleteMessage(queueUrl: String, receiptHandle: String): DeleteMessageResult {
    return client().deleteMessage(queueUrl, receiptHandle)
  }

  override fun shutdown() {
    unbufferedSqs.shutdown()
    // NB: buffered client shutdown should be immediate since we only buffer outgoing send/delete requests and should
    // never be using receive pre-fetching (receive pre-fetch keeps bg threads running so this could take a few secs).
    bufferedSqs.shutdown()
  }

  private fun client() : AmazonSQS {
    return if (featureFlags.getBoolean(FEATURE, appName)) {
      bufferedSqs
    } else {
      unbufferedSqs
    }
  }

  companion object {
    val FEATURE = Feature("jobqueue-buffered-sqs-client")
  }
}
