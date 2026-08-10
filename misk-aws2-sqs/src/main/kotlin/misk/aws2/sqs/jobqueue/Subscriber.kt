package misk.aws2.sqs.jobqueue

import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runInterruptible
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.inject.AsyncSwitch
import misk.jobqueue.QueueName
import misk.jobqueue.v2.BlockingJobHandler
import misk.jobqueue.v2.JobHandler
import misk.jobqueue.v2.JobStatus
import misk.jobqueue.v2.SuspendingJobHandler
import misk.logging.getLogger
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

/**
 * Subscriber reads jobs from the channel and passes them to handler.
 *
 * It responds to handler results by either acknowledging the job or moving it to a dead letter queue.
 */
class Subscriber(
  val queueName: QueueName,
  val queueConfig: SqsQueueConfig,
  val deadLetterQueueName: QueueName,
  val handler: JobHandler,
  val channel: Channel<SqsJob>,
  val client: SqsAsyncClient,
  val sqsQueueResolver: SqsQueueResolver,
  val sqsMetrics: SqsMetrics,
  val moshi: Moshi,
  val clock: Clock,
  val tracer: Tracer,
  val visibilityTimeoutCalculator: VisibilityTimeoutCalculator,
  val asyncSwitch: AsyncSwitch,
) {
  private var wasDisabled = false
  private val draining = AtomicBoolean(false)
  private val inFlightReceives = ConcurrentHashMap.newKeySet<CompletableFuture<ReceiveMessageResponse>>()
  private val activeJobs = AtomicInteger(0)
  private val queuedHandoffs = AtomicInteger(0)

  /**
   * Puts the subscriber into draining mode: no new SQS receive requests are issued and any in-flight receive is
   * cancelled. Jobs already handed off stay owned - they remain in the channel and are handled normally until the
   * channel is closed and empty. Returns the number of in-flight jobs at drain start.
   *
   * Best effort: cancelling an in-flight receive is a race against SQS assigning messages to that request server-side.
   * Messages inside a cancelled receive were never seen by this consumer but have started their visibility timeout;
   * they become receivable again only after it expires. The alternative - waiting out the long poll - would delay
   * shutdown by up to the configured wait timeout, so prompt exit is preferred and the window is accepted and
   * documented rather than eliminated.
   */
  internal fun startDraining(): Int {
    draining.set(true)
    inFlightReceives.forEach { it.cancel(true) }
    return inFlightCount()
  }

  /** Number of jobs that have been handed off from polling but have not finished handling. */
  internal fun inFlightCount(): Int = activeJobs.get() + queuedHandoffs.get()

  suspend fun run() {
    while (true) {
      val job =
        try {
          tracer.withSpan("channel-receive-queue-${queueName.value}") { channel.receive() }
        } catch (e: ClosedReceiveChannelException) {
          return
        }
      queuedHandoffs.decrementAndGet()
      activeJobs.incrementAndGet()
      try {
        val result = process(job)
        if (draining.get()) {
          val label = result?.name?.lowercase() ?: "handler_failed"
          sqsMetrics.drainJobsCompleted.labels(queueName.value, label).inc()
        }
      } finally {
        activeJobs.decrementAndGet()
      }
    }
  }

  /** Handles a single job. Returns the handler's status, or null if the handler threw. */
  private suspend fun process(job: SqsJob): JobStatus? {
    return tracer.withSpan("process-queue-${queueName.value}") {
      val receiveFromChannelTimestamp = clock.millis()
      sqsMetrics.channelReceiveLag
        .labels(queueName.value)
        .observe((receiveFromChannelTimestamp - job.publishToChannelTimestamp).toDouble())
      val result =
        try {
          val startTime = clock.millis()
          val result =
            tracer.withSpan("handle-queue-${queueName.value}") {
              when (handler) {
                is SuspendingJobHandler -> handler.handleJob(job)
                is BlockingJobHandler -> runInterruptible { handler.handleJob(job) }
              }
            }
          sqsMetrics.handlerDispatchTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())
          result
        } catch (e: Exception) {
          // Propagate cancellation of this subscriber, but recover if only the failed operation was canceled.
          currentCoroutineContext().ensureActive()
          logger.warn(e) { "Handler failed for job ${job.id} from queue ${job.queueName.value}" }
          sqsMetrics.handlerFailures.labels(queueName.value).inc()
          return@withSpan null
        }
      when (result) {
        JobStatus.OK -> deleteMessage(job)
        JobStatus.DEAD_LETTER -> deadLetterMessage(job)
        JobStatus.RETRY_WITH_BACKOFF -> retryWithBackoff(job)
        JobStatus.RETRY_LATER -> {
          /* no-op, will be retried after visibility timeout passes */
        }
      }
      result
    }
  }

  private suspend fun <T> Tracer.withSpan(spanName: String, block: suspend () -> T): T {
    val span = tracer.buildSpan(spanName).start()
    val scope = scopeManager().activate(span)
    try {
      return block()
    } catch (t: Throwable) {
      Tags.ERROR.set(span, true)
      throw t
    } finally {
      scope.close()
      span.finish()
    }
  }

  private suspend fun retryWithBackoff(job: SqsJob) {
    try {
      val visibilityTime =
        visibilityTimeoutCalculator.calculateVisibilityTimeout(
          currentReceiveCount =
            job.message.attributes()[MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT]?.toInt() ?: 1,
          queueVisibilityTimeout = queueConfig.visibility_timeout ?: 1,
        )

      client
        .changeMessageVisibility(
          ChangeMessageVisibilityRequest.builder()
            .queueUrl(job.queueUrl)
            .receiptHandle(job.message.receiptHandle())
            .visibilityTimeout(visibilityTime)
            .build()
        )
        .await()

      sqsMetrics.visibilityTime.labels(queueName.value).observe(visibilityTime.toDouble())
    } catch (e: Exception) {
      // Propagate cancellation of this subscriber, but recover if only the failed operation was canceled.
      currentCoroutineContext().ensureActive()
      logger.warn(e) { "Failed to retry job ${job.id} with backoff from queue ${job.queueName.value}" }
      sqsMetrics.jobsFailedToRetryWithBackoff.labels(queueName.value).inc()
    }
  }

  /**
   * Removes job from the queue.
   *
   * We may fail to acknowledge a job if visibility timeout is too short comparing to processing time, and we get
   * concurrent acknowledgments
   */
  private suspend fun deleteMessage(job: SqsJob) {
    val startTime = clock.millis()
    try {
      client
        .deleteMessage(
          DeleteMessageRequest.builder().queueUrl(job.queueUrl).receiptHandle(job.message.receiptHandle()).build()
        )
        .await()
    } catch (e: Exception) {
      // Propagate cancellation of this subscriber, but recover if only the failed operation was canceled.
      currentCoroutineContext().ensureActive()
      logger.warn(e) { "Failed to acknowledge job ${job.idempotenceKey} from queue ${job.queueName.value}" }
      sqsMetrics.jobsFailedToAcknowledge.labels(queueName.value).inc()
      if (draining.get()) {
        sqsMetrics.drainAckFailures.labels(queueName.value).inc()
      }
      return
    }
    sqsMetrics.sqsDeleteTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())
    sqsMetrics.jobsAcknowledged.labels(queueName.value).inc()
    if (draining.get()) {
      sqsMetrics.drainAcksSucceeded.labels(queueName.value).inc()
    }
  }

  private suspend fun deadLetterMessage(job: SqsJob) {
    try {
      val deadLetterQueueUrl = sqsQueueResolver.getQueueUrl(deadLetterQueueName)
      val startTime = clock.millis()

      client
        .sendMessage(
          SendMessageRequest.builder()
            .queueUrl(deadLetterQueueUrl)
            .messageBody(job.body)
            .messageAttributes(job.message.messageAttributes())
            .build()
        )
        .await()
      sqsMetrics.sqsSendTime.labels(deadLetterQueueName.value).observe((clock.millis() - startTime).toDouble())
      sqsMetrics.jobsDeadLettered.labels(queueName.value).inc()
    } catch (e: Exception) {
      // Propagate cancellation of this subscriber, but recover if only the failed operation was canceled.
      currentCoroutineContext().ensureActive()
      logger.warn(e) { "Failed to dead-letter job ${job.id} from queue ${job.queueName.value}" }
      sqsMetrics.jobsFailedToDeadLetter.labels(queueName.value).inc()
      return
    }

    // Follow by removing the message from the queue
    deleteMessage(job)
  }

  /** Polls the messages from both the regular and the retry queue. */
  suspend fun poll() {
    if (queueConfig.install_retry_queue) {
        merge(messageFlow(queueName), messageFlow(queueName.retryQueue))
      } else {
        messageFlow(queueName)
      }
      .collect { received -> channel.send(received) }
  }

  private fun messageFlow(queueName: QueueName) = flow {
    val queueUrl = sqsQueueResolver.getQueueUrl(queueName)
    while (!draining.get()) {
      if (!asyncSwitch.isEnabled("sqs")) {
        if (!wasDisabled) {
          logger.info { "Async SQS tasks disabled. Polling paused for queue ${queueName.value}." }
          wasDisabled = true
        }
        delay(1000)
        continue
      }
      if (wasDisabled) {
        logger.info { "Async SQS tasks re-enabled. Polling resuming for queue ${queueName.value}." }
        wasDisabled = false
      }
      val startTime = clock.millis()
      val future = fetchMessages(queueUrl)
      inFlightReceives.add(future)
      if (draining.get()) {
        // The drain flag was set between the loop check and issuing the receive. startDraining may have missed
        // this future when cancelling, so cancel it here and record that a receive raced with the drain.
        sqsMetrics.receiveAttemptsAfterDrain.labels(queueName.value).inc()
        future.cancel(true)
      }
      val response =
        try {
          future.await()
        } catch (e: Exception) {
          // Propagate cancellation of this subscriber, but recover if only the failed operation was canceled.
          currentCoroutineContext().ensureActive()
          if (draining.get()) {
            logger.info { "Polling stopped for queue ${queueName.value}: consumer is draining" }
            break
          }
          logger.warn(e) { "Failed to fetch messages from queue ${queueName.value}; retrying" }
          sqsMetrics.sqsReceiveFailures.labels(queueName.value).inc()
          continue
        } finally {
          inFlightReceives.remove(future)
        }
      sqsMetrics.sqsReceiveTime.labels(queueName.value).observe((clock.millis() - startTime).toDouble())

      sqsMetrics.jobsReceived.labels(queueName.value).inc(response.messages().size.toDouble())
      response.messages().forEach { message ->
        message.attributes()[MessageSystemAttributeName.SENT_TIMESTAMP]?.let {
          val sentTimestamp = it.toLong()
          val processingLag = clock.instant().minusMillis(sentTimestamp).toEpochMilli().toDouble()
          val receiveCounter = message.attributes()[MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT]?.toInt()
          if (receiveCounter == 1) {
            sqsMetrics.queueFirstProcessingLag.labels(queueName.value).observe(processingLag)
          }
          sqsMetrics.queueProcessingLag.labels(queueName.value).observe(processingLag)
        }
        val publishToChannelTimestamp = clock.millis()
        queuedHandoffs.incrementAndGet()
        emit(
          SqsJob(
            queueName = queueName,
            moshi = moshi,
            message = message,
            queueUrl = queueUrl,
            publishToChannelTimestamp = publishToChannelTimestamp,
          )
        )
      }
    }
  }

  private fun fetchMessages(queueUrl: String): CompletableFuture<ReceiveMessageResponse> {
    val request =
      ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageAttributeNames(MessageSystemAttributeName.ALL.toString())
        .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
        .maxNumberOfMessages(queueConfig.max_number_of_messages)
        .waitTimeSeconds(queueConfig.wait_timeout)
        .visibilityTimeout(queueConfig.visibility_timeout)
        .build()
    return client.receiveMessage(request)
  }

  companion object {
    private val logger = getLogger<Subscriber>()
  }
}
