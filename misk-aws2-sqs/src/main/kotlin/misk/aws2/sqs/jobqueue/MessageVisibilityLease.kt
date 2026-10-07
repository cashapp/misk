package misk.aws2.sqs.jobqueue

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import misk.aws2.sqs.jobqueue.config.SqsVisibilityHeartbeatConfig
import misk.logging.getLogger
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest

/** Owned by the polling scope, whose dispatcher must remain independent of blocking handlers. */
internal class MessageVisibilityLease(
  scope: CoroutineScope,
  private val client: SqsAsyncClient,
  private val job: SqsJob,
  private val config: SqsVisibilityHeartbeatConfig,
  private val receivedAt: TimeMark,
  private val timeSource: TimeSource,
) {
  private val owner = Job(scope.coroutineContext[Job])
  private val leaseScope = CoroutineScope(scope.coroutineContext + owner)
  private val finished = CompletableDeferred<Unit>()
  @Volatile private var visibleAt = receivedAt + config.visibility_timeout.seconds

  init {
    leaseScope.launch {
      delay(minOf(config.processing_timeout_ms - receivedAt.elapsedNow().inWholeMilliseconds, remainingMs()))
      owner.cancel(CancellationException("SQS message processing deadline exceeded"))
    }
  }

  private val heartbeat =
    leaseScope.launch {
      try {
        while (withTimeoutOrNull(config.interval_ms) { finished.await() } == null) {
          check(!visibleAt.hasPassedNow()) { "SQS message visibility expired before renewal" }
          val visibility = minOf(config.visibility_timeout, (remainingMs() / 1000).toInt())
          check(visibility > 0) { "SQS receive reached its twelve-hour limit" }
          val startedAt = timeSource.markNow()
          // A stuck SQS call must not keep the handler or acknowledgement waiting indefinitely.
          withTimeout(minOf(10_000L, -visibleAt.elapsedNow().inWholeMilliseconds)) {
            client
              .changeMessageVisibility(
                ChangeMessageVisibilityRequest.builder()
                  .queueUrl(job.queueUrl)
                  .receiptHandle(job.message.receiptHandle())
                  .visibilityTimeout(visibility)
                  .build()
              )
              .await()
          }
          visibleAt = startedAt + visibility.seconds
        }
      } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        logger.warn(e) { "Lost SQS visibility renewal for job ${job.id} from queue ${job.queueName.value}" }
        owner.cancel(CancellationException("SQS visibility renewal failed", e))
      }
    }

  suspend fun <T> whileOwned(block: suspend () -> T): T = coroutineScope {
    // Keep handler cancellation linked to BOTH its subscriber and the independent visibility deadline.
    val processing = currentCoroutineContext().job
    val registration =
      owner.invokeOnCompletion { cause ->
        processing.cancel(CancellationException("SQS message ownership ended", cause))
      }
    try {
      ensureOwned()
      block()
    } finally {
      registration.dispose()
    }
  }

  suspend fun stopRenewing() {
    finished.complete(Unit)
    // Let an in-flight renewal finish before DeleteMessage or retry backoff changes this receipt.
    heartbeat.join()
    ensureOwned()
  }

  private fun ensureOwned() {
    if (visibleAt.hasPassedNow()) owner.cancel(CancellationException("SQS message visibility expired"))
    owner.ensureActive()
  }

  fun close() {
    owner.cancel()
  }

  // SQS's maximum is measured from ReceiveMessage, not the most recent renewal. Starting at the request is
  // conservative.
  private fun remainingMs() = 43_195_000L - receivedAt.elapsedNow().inWholeMilliseconds

  companion object {
    private val logger = getLogger<MessageVisibilityLease>()
  }
}
