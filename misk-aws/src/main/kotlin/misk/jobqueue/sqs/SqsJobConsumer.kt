package misk.jobqueue.sqs

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provider
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import misk.annotation.ExperimentalMiskApi
import misk.inject.AsyncSwitch
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.jobqueue.BatchJobHandler
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.logging.SmartTagsThreadLocalHandler
import misk.logging.error
import misk.logging.getLogger
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import misk.time.timed
import misk.tracing.traceWithNewRootSpan
import org.slf4j.MDC

@Singleton
internal class SqsJobConsumer
@Inject
internal constructor(
  @ForSqsHandling private val handlingThreads: ExecutorService,
  @ForSqsHandling private val taskQueue: RepeatedTaskQueue,
  @ForSqsReceiving private val receivingThreads: ExecutorService,
  private val sqsConsumerAllocator: SqsConsumerAllocator,
  private val featureFlags: FeatureFlags,
  private val metrics: SqsMetrics,
  private val moshi: Moshi,
  private val queues: QueueResolver,
  private val serviceManagerProvider: Provider<ServiceManager>,
  private val tracer: Tracer,
  private val clock: Clock,
  awsSqsJobQueueConfig: AwsSqsJobQueueConfig,
  private val asyncSwitch: AsyncSwitch,
) : JobConsumer {
  private val receiverPolicy = awsSqsJobQueueConfig.aws_sqs_job_receiver_policy

  private val subscriptions = ConcurrentHashMap<QueueName, QueueReceiver>()
  private var wasDisabled = false

  override fun subscribe(queueName: QueueName, handler: JobHandler) {
    subscribe(queueName, IndividualQueueReceiver(queueName, handler))
  }

  override fun subscribe(queueName: QueueName, handler: BatchJobHandler) {
    subscribe(queueName, BatchQueueReceiver(queueName, handler, clock))
  }

  private fun subscribe(queueName: QueueName, receiver: QueueReceiver) {
    check(subscriptions.putIfAbsent(queueName, receiver) == null) { "already subscribed to queue ${queueName.value}" }

    log.info { "subscribing to queue ${queueName.value}" }
    taskQueue.scheduleWithBackoff(Duration.ZERO) {
      if (!asyncSwitch.isEnabled("sqs")) {
        if (!wasDisabled) {
          log.info { "Async SQS tasks disabled. Consumer paused." }
          wasDisabled = true
        }
        return@scheduleWithBackoff Status.NO_WORK
      }
      if (wasDisabled) {
        log.info { "Async SQS tasks re-enabled. Consumer resuming." }
        wasDisabled = false
      }

      // Don't call handlers until all services are ready, otherwise handlers will crash because
      // the services they might need (databases, etc.) won't be ready.
      if (serviceManagerProvider.get().isHealthy) {
        receiver.run()
      } else {
        Status.NO_WORK
      }
    }
  }

  override fun unsubscribe(queueName: QueueName) {
    log.info { "unsubscribing from queue ${queueName.value}" }
    subscriptions[queueName]?.stop()
  }

  internal fun getReceiver(queueName: QueueName): QueueReceiver {
    return subscriptions[queueName]!!
  }

  internal fun unsubscribeAll() {
    subscriptions.keys.forEach { unsubscribe(it) }
  }

  fun shutDown() {
    log.info { "shutting down queue consumer threads" }
    receivingThreads.shutdown()
    // Giving it some time to the receivers & handlers to finish.
    receivingThreads.awaitTermination(10, TimeUnit.SECONDS)
    handlingThreads.shutdown()
    handlingThreads.awaitTermination(10, TimeUnit.SECONDS)
  }

  internal abstract inner class QueueReceiver(queueName: QueueName) {
    val queue = queues.getForReceiving(queueName)
    protected val shouldKeepRunning = AtomicBoolean(true)

    protected abstract fun receive(): List<Status>

    fun stop() {
      shouldKeepRunning.set(false)
    }

    fun run(): Status {
      if (!shouldKeepRunning.get()) {
        log.info { "shutting down receiver for ${queue.queueName}" }
        return Status.NO_RESCHEDULE
      }
      val size = sqsConsumerAllocator.computeSqsConsumersForPod(queue.name, receiverPolicy)
      val futures = List(size) { CompletableFuture.supplyAsync({ receive() }, receivingThreads) }

      // Either all messages are consumed and processed successfully, or we signal failure.
      // If none of the received consume any messages, return NO_WORK for backoff.
      return CompletableFuture.allOf(*futures.toTypedArray())
        .thenApply {
          futures
            .flatMap { it.join() }
            .onEach { check(it in listOf(Status.FAILED, Status.OK, Status.NO_WORK)) }
            .fold(Status.NO_WORK) { finalStatus, status ->
              when {
                status == Status.FAILED -> status
                status == Status.OK && finalStatus != Status.FAILED -> status
                status == Status.NO_WORK && finalStatus == Status.NO_WORK -> status
                else -> finalStatus
              }
            }
        }
        .join()
    }

    /**
     * The maximum number of messages we will fetch at a time. We might issue multiple requests to SQS to fetch these
     * messages, depending on the QueueReceiver implementation.
     */
    protected fun batchSize() = featureFlags.getInt(CONSUMERS_BATCH_SIZE, queue.queueName)

    /**
     * Issues a single request to SQS to fetch messages.
     *
     * @param sqsBatchSize the number of messages to fetch, max 10
     * @param waitTimeSeconds 0 to short poll, > 0 to long poll, null uses the queue's default
     */
    protected fun fetchMessages(sqsBatchSize: Int, waitTimeSeconds: Int? = null): List<SqsJob> {
      check(sqsBatchSize <= SQS_MAX_BATCH_SIZE) {
        "Batch size $sqsBatchSize but SQS supports a max of $SQS_MAX_BATCH_SIZE messages per batch"
      }
      val messages =
        try {
          metrics.sqsReceiveTime.timedMills(queue.queueName, queue.queueName) {
            queue.call { client ->
              var receiveRequest =
                ReceiveMessageRequest()
                  .withAttributeNames("All")
                  .withMessageAttributeNames("All")
                  .withQueueUrl(queue.url)
                  .withMaxNumberOfMessages(sqsBatchSize)

              if (waitTimeSeconds != null) {
                receiveRequest = receiveRequest.withWaitTimeSeconds(waitTimeSeconds)
              }

              client.receiveMessage(receiveRequest).messages
            }
          }
        } catch (e: ClientExecutionTimeoutException) {
          log.info("timed out long polling for messages from ${queue.queueName}")
          emptyList<Message>()
        }

      for (message in messages) {
        try {
          val sentTimestamp = message.attributes[SQS_ATTRIBUTE_SENT_TIMESTAMP]!!.toLong()
          val receiveCount = message.attributes[SQS_ATTRIBUTE_APPROX_RECEIVE_COUNT]!!.toLong()

          if (receiveCount <= 1) {
            // Calculate milliseconds between received time and when job was sent.
            val processingLag = clock.instant().minusMillis(sentTimestamp).toEpochMilli()
            metrics.queueProcessingLag.record(processingLag.toDouble(), queue.queueName, queue.queueName)
          }
        } catch (e: NumberFormatException) {
          log.warn("Message ${message.messageId} had invalid SentTimestamp format")
        } catch (e: NullPointerException) {
          log.warn("Message ${message.messageId} was missing SentTimestamp or ApproximateReceiveCount")
        }
      }

      return messages.map { SqsJob(queue.name, queues, metrics, moshi, it) }
    }
  }

  internal inner class IndividualQueueReceiver(queueName: QueueName, private val handler: JobHandler) :
    QueueReceiver(queueName) {

    override fun receive(): List<Status> {
      val messages = fetchMessages(batchSize())

      if (messages.isEmpty()) {
        return listOf(Status.NO_WORK)
      }

      val futures = handleMessages(messages)

      return CompletableFuture.allOf(*futures.toTypedArray()).thenApply { futures.map { it.join() } }.join()
    }

    @OptIn(ExperimentalMiskApi::class)
    private fun handleMessages(messages: List<SqsJob>): List<CompletableFuture<Status>> {
      return messages.map { message ->
        CompletableFuture.supplyAsync(
          {
            metrics.jobsReceived.labels(queue.queueName, queue.queueName).inc()

            tracer.traceWithNewRootSpan("handle-job-${queue.queueName}") { span ->
              // If the incoming job has an original trace id, set that as a tag on the new span.
              // We don't turn that into the parent of the current span because that would
              // incorrectly include the execution time of the job in the execution time of the
              // action that triggered the job
              message.attributes[SqsJob.ORIGINAL_TRACE_ID_ATTR]?.let { ORIGINAL_TRACE_ID_TAG.set(span, it) }

              // Run the handler and record timing
              try {
                MDC.put(SQS_JOB_ID_MDC, message.id)
                MDC.put(SQS_JOB_ID_STRUCTURED_MDC, message.id)
                MDC.put(SQS_QUEUE_NAME_MDC, message.queueName.value)
                MDC.put(SQS_QUEUE_TYPE_MDC, SQS_QUEUE_TYPE)
                val (duration) = timed { handler.handleJob(message) }
                metrics.handlerDispatchTime.record(duration.toMillis().toDouble(), queue.queueName, queue.queueName)
                Status.OK
              } catch (th: Throwable) {
                val mdcTags = SmartTagsThreadLocalHandler.popThreadLocalSmartTags()

                log.error(th, *mdcTags.toTypedArray()) { "error handling job from ${queue.queueName}" }

                metrics.handlerFailures.labels(queue.queueName, queue.queueName).inc()
                Tags.ERROR.set(span, true)
                Status.FAILED
              } finally {
                MDC.remove(SQS_JOB_ID_MDC)
                MDC.remove(SQS_JOB_ID_STRUCTURED_MDC)
                MDC.remove(SQS_QUEUE_NAME_MDC)
                MDC.remove(SQS_QUEUE_TYPE_MDC)
              }
            }
          },
          handlingThreads,
        )
      }
    }
  }

  internal inner class BatchQueueReceiver(
    queueName: QueueName,
    private val handler: BatchJobHandler,
    private val clock: Clock,
  ) : QueueReceiver(queueName) {

    private fun receiveWaitTimeSeconds() = featureFlags.getInt(CONSUMERS_RECEIVE_WAIT_TIME_SECONDS, queue.queueName)

    private fun batchWaitTimeSeconds() = featureFlags.getInt(CONSUMERS_BATCH_WAIT_TIME_SECONDS, queue.queueName)

    override fun receive(): List<Status> {
      // Threads poll concurrently, fetching up to batchSize messages.
      // If batchWaitTimeSeconds is > 0, threads will keep polling until batchSize is reached
      // or batchWaitTimeSeconds have passed.
      val deadline = clock.instant().plusSeconds(batchWaitTimeSeconds().toLong())
      val remainingMessages = Semaphore(batchSize())
      val numThreads = ceil(batchSize() / SQS_MAX_BATCH_SIZE.toDouble()).toInt()
      val batch =
        (1..numThreads)
          .map { receivingThreads.submit(ReceiverCallable(remainingMessages, deadline)) }
          .flatMap { it.get() }

      if (batch.isEmpty()) {
        return listOf(Status.NO_WORK)
      }

      return listOf(handleMessages(batch))
    }

    @OptIn(ExperimentalMiskApi::class)
    private fun handleMessages(messages: List<SqsJob>): Status {
      metrics.jobsReceived.labels(queue.queueName, queue.queueName).inc(messages.size.toDouble())

      return tracer.traceWithNewRootSpan("handle-job-${queue.queueName}") { span ->
        // Run the handler and record timing
        try {
          MDC.put(SQS_QUEUE_NAME_MDC, messages.first().queueName.value)
          MDC.put(SQS_QUEUE_TYPE_MDC, SQS_QUEUE_TYPE)
          val (duration) = timed { handler.handleJobs(messages) }
          metrics.handlerDispatchTime.record(duration.toMillis().toDouble(), queue.queueName, queue.queueName)
          Status.OK
        } catch (th: Throwable) {
          val mdcTags = SmartTagsThreadLocalHandler.popThreadLocalSmartTags()

          log.error(th, *mdcTags.toTypedArray()) { "error handling job from ${queue.queueName}" }

          metrics.handlerFailures.labels(queue.queueName, queue.queueName).inc()
          Tags.ERROR.set(span, true)
          Status.FAILED
        } finally {
          MDC.remove(SQS_QUEUE_NAME_MDC)
          MDC.remove(SQS_QUEUE_TYPE_MDC)
        }
      }
    }

    private inner class ReceiverCallable(private val remainingMessages: Semaphore, private val deadline: Instant) :
      Callable<List<SqsJob>> {

      private fun acquirePermits() =
        if (remainingMessages.tryAcquire(SQS_MAX_BATCH_SIZE)) {
          SQS_MAX_BATCH_SIZE
        } else {
          val remaining = remainingMessages.drainPermits()
          if (remaining > SQS_MAX_BATCH_SIZE) {
            remainingMessages.release(remaining - SQS_MAX_BATCH_SIZE)
            SQS_MAX_BATCH_SIZE
          } else {
            remaining
          }
        }

      private fun releasePermits(messages: Int) {
        remainingMessages.release(messages)
      }

      override fun call(): List<SqsJob> = buildList {
        do {
          val sqsBatchSize = acquirePermits()
          if (sqsBatchSize == 0) {
            // This either means we already fetched the target number of messages, or other
            // threads are busy fetching them. In either case, this thread can stop polling.
            break
          }

          // We might exceed the deadline by some milliseconds. That's ok.
          val waitTimeSeconds =
            min(receiveWaitTimeSeconds(), (deadline.epochSecond - clock.instant().epochSecond).toInt()).let {
              max(0, it)
            }

          val messages = fetchMessages(sqsBatchSize, waitTimeSeconds)
          addAll(messages)
          releasePermits(sqsBatchSize - messages.size)
        } while (clock.instant().isBefore(deadline) && shouldKeepRunning.get())
      }
    }
  }

  companion object {
    private val log = getLogger<SqsJobConsumer>()
    internal val POD_CONSUMERS_PER_QUEUE = Feature("pod-jobqueue-consumers")
    internal val POD_MAX_JOBQUEUE_CONSUMERS = Feature("pod-max-jobqueue-consumers")
    internal val CONSUMERS_PER_QUEUE = Feature("jobqueue-consumers")
    internal val CONSUMERS_BATCH_SIZE = Feature("jobqueue-consumers-fetch-batch-size")
    internal val CONSUMERS_BATCH_WAIT_TIME_SECONDS = Feature("jobqueue-consumers-batch-wait-time")
    internal val CONSUMERS_RECEIVE_WAIT_TIME_SECONDS = Feature("jobqueue-consumers-receive-wait-time")
    private val ORIGINAL_TRACE_ID_TAG = StringTag("original.trace_id")
    private const val SQS_JOB_ID_MDC = "sqs_job_id"
    private const val SQS_QUEUE_TYPE_MDC = "misk.job_queue.queue_type"
    private const val SQS_JOB_ID_STRUCTURED_MDC = "misk.job_queue.job_id"
    private const val SQS_QUEUE_NAME_MDC = "misk.job_queue.queue_name"
    private const val SQS_QUEUE_TYPE = "aws-sqs"
    private const val SQS_ATTRIBUTE_SENT_TIMESTAMP = "SentTimestamp"
    private const val SQS_ATTRIBUTE_APPROX_RECEIVE_COUNT = "ApproximateReceiveCount"
    private const val SQS_MAX_BATCH_SIZE = 10
  }
}
