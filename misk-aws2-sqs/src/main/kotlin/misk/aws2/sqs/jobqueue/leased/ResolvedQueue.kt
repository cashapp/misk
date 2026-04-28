package misk.aws2.sqs.jobqueue.leased

import java.util.concurrent.CompletionException
import java.util.concurrent.CompletableFuture
import misk.cloud.aws.AwsAccountId
import misk.cloud.aws.AwsRegion
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.jobqueue.QueueName
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.batchmanager.SqsAsyncBatchManager

/** [ResolvedQueue] provides information needed to reach an SQS queue */
internal class ResolvedQueue(
  val name: QueueName,
  val sqsQueueName: QueueName,
  val url: String,
  val region: AwsRegion,
  val accountId: AwsAccountId,
  val client: SqsClient,
  val batchManager: SqsAsyncBatchManager,
  private val appName: String,
  private val featureFlags: FeatureFlags,
  val maxRetries: Int = 10,
) {

  val queueName: String
    get() = name.value

  /**
   * Invokes the lambda with this queue's [SqsClient]. Exceptions thrown by the client are wrapped in a [SQSException].
   */
  fun <T> call(lambda: (SqsClient) -> T): T {
    try {
      return lambda.invoke(client)
    } catch (e: ApiCallAttemptTimeoutException) {
      throw e
    } catch (e: SdkException) {
      throw SQSException(e, this)
    }
  }

  /** Invokes send operations using the same buffered-client feature flag as the SDK v1 implementation. */
  fun <T> callSend(
    unbufferedLambda: (SqsClient) -> T,
    bufferedLambda: (SqsAsyncBatchManager) -> CompletableFuture<T>,
  ): T {
    return if (featureFlags.getBoolean(BUFFERED_SQS_CLIENT, appName)) {
      callBatchManager(bufferedLambda)
    } else {
      call(unbufferedLambda)
    }
  }

  private fun <T> callBatchManager(lambda: (SqsAsyncBatchManager) -> CompletableFuture<T>): T {
    try {
      return lambda.invoke(batchManager).join()
    } catch (e: CompletionException) {
      val cause = e.cause
      if (cause is SdkException) throw SQSException(cause, this)
      throw e
    } catch (e: SdkException) {
      throw SQSException(e, this)
    }
  }

  /** Wraps AWS client errors, adding queue metadata to the exception message */
  class SQSException(cause: SdkException, queue: ResolvedQueue) :
    RuntimeException("${cause.message} (sqsQueue=${queue.sqsQueueName} region=${queue.region})", cause)

  companion object {
    private val BUFFERED_SQS_CLIENT = Feature("jobqueue-buffered-sqs-client")
  }
}
