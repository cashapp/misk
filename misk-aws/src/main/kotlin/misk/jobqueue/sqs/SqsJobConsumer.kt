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
import misk.annotation.ExperimentalMiskApi
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.jobqueue.BatchJobHandler
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.jobqueue.subscribe
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import misk.time.timed
import org.slf4j.MDC
import wisp.logging.TaggedLogger
import wisp.logging.error
import wisp.logging.getLogger
import wisp.tracing.traceWithNewRootSpan
import java.time.Clock
import java.time.Duration
import java.util.Queue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
internal class SqsJobConsumer @Inject internal constructor(
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
  awsSqsJobQueueConfig: AwsSqsJobQueueConfig
) : JobConsumer {
  private val receiverPolicy = awsSqsJobQueueConfig.aws_sqs_job_receiver_policy

  private val subscriptions = ConcurrentHashMap<QueueName, QueueReceiver>()

  override fun subscribe(queueName: QueueName, handler: JobHandler) {
    subscribe(queueName, QueueReceiver(queueName, individualHandler = handler))
  }

  override fun subscribe(queueName: QueueName, handler: BatchJobHandler) {
    subscribe(queueName, QueueReceiver(queueName, batchHandler = handler))
  }

  private fun subscribe(queueName: QueueName, receiver: QueueReceiver) {
    check(subscriptions.putIfAbsent(queueName, receiver) == null) {
      "already subscribed to queue ${queueName.value}"
    }

    log.info {
      "subscribing to queue ${queueName.value}"
    }
    taskQueue.scheduleWithBackoff(Duration.ZERO) {

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
    subscriptions[queueName]?.stop()
  }

  internal fun getReceiver(queueName: QueueName): QueueReceiver {
    return subscriptions[queueName]!!
  }

  fun shutDown() {
    receivingThreads.shutdown()
    handlingThreads.shutdown()
    // Giving it some time to the handlers to finish.
    handlingThreads.awaitTermination(10, TimeUnit.SECONDS)
  }

  internal inner class QueueReceiver(
    queueName: QueueName,
    private val individualHandler: JobHandler? = null,
    private val batchHandler: BatchJobHandler? = null,
  ) {
    private val queue = queues.getForReceiving(queueName)
    private val shouldKeepRunning = AtomicBoolean(false)

    init {
      check((individualHandler != null).xor(batchHandler != null)) {
        "Only one of individualHandler or batchHandler may be specified"
      }
    }

    fun stop() {
      shouldKeepRunning.set(false)
    }

    fun run(): Status {
      if (!shouldKeepRunning.get()) {
        Status.NO_RESCHEDULE
      }
      val size = sqsConsumerAllocator.computeSqsConsumersForPod(queue.name, receiverPolicy)
      val futures = List(size) {
        CompletableFuture.supplyAsync({ receive() }, receivingThreads)
      }

      // Either all messages are consumed and processed successfully, or we signal failure.
      // If none of the received consume any messages, return NO_WORK for backoff.
      return CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
        futures.flatMap { it.join() }
          .onEach { check(it in listOf(Status.FAILED, Status.OK, Status.NO_WORK)) }
          .fold(Status.NO_WORK) { finalStatus, status ->
            when {
              status == Status.FAILED -> status
              status == Status.OK && finalStatus != Status.FAILED -> status
              status == Status.NO_WORK && finalStatus == Status.NO_WORK -> status
              else -> finalStatus
            }
          }
      }.join()
    }

    private fun fetchMessages(): List<SqsJob> {
      val messages = try {
        metrics.sqsReceiveTime.timedMills(queue.queueName, queue.queueName) {
          queue.call { client ->
            val receiveRequest = ReceiveMessageRequest()
              .withAttributeNames("All")
              .withMessageAttributeNames("All")
              .withQueueUrl(queue.url)
              .withMaxNumberOfMessages(batchSize())

            client.receiveMessage(receiveRequest).messages
          }
        }
      } catch (e: ClientExecutionTimeoutException) {
        log.info("timed out long polling for messages from ${queue.queueName}")
        emptyList<Message>()
      }

      for(message in messages) {
        try {
          val sentTimestamp = message.attributes[SQS_ATTRIBUTE_SENT_TIMESTAMP]!!.toLong()
          val receiveCount = message.attributes[SQS_ATTRIBUTE_APPROX_RECEIVE_COUNT]!!.toLong()

          if (receiveCount <= 1) {
            // Calculate milliseconds between received time and when job was sent.
            val processingLag = clock.instant().minusMillis(sentTimestamp).toEpochMilli()
            metrics.queueProcessingLag.record(
              processingLag.toDouble(),
              queue.queueName,
              queue.queueName
            )
          }


        } catch (e: NumberFormatException) {
          log.warn("Message ${message.messageId} had invalid SentTimestamp format")
        } catch (e: NullPointerException) {
          log.warn("Message ${message.messageId} was missing SentTimestamp or ApproximateReceiveCount")
        }
      }

      return messages.map { SqsJob(queue.name, queues, metrics, moshi, it) }
    }

    private fun batchSize() = featureFlags.getInt(CONSUMERS_BATCH_SIZE, queue.queueName)

    private fun receive(): List<Status> {
      val messages = fetchMessages()

      if (messages.isEmpty()) {
        return listOf(Status.NO_WORK)
      }

      val futures = handleMessages(messages)

      return CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
        futures.map { it.join() }
      }.join()
    }

    private fun handleMessages(messages: List<SqsJob>): List<CompletableFuture<Status>> {
      return if (batchHandler != null) {
        metrics.jobsReceived.labels(queue.queueName, queue.queueName).inc(messages.size.toDouble())
        val status = invokeHandler(messages, useBatchHandler = true)
        return listOf(CompletableFuture.completedFuture(status))
      } else {
        messages.map { message ->
          CompletableFuture.supplyAsync(
            {
              metrics.jobsReceived.labels(queue.queueName, queue.queueName).inc()
              invokeHandler(listOf(message), useBatchHandler = false)
            },
            handlingThreads
          )
        }
      }
    }

    @OptIn(ExperimentalMiskApi::class)
    private fun invokeHandler(messages: List<SqsJob>, useBatchHandler: Boolean): Status {
      check(useBatchHandler || messages.size == 1) {
        "Trying to invoke individual handler with multiple messages"
      }
      val firstMessage = messages.first()

      return tracer.traceWithNewRootSpan("handle-job-${queue.queueName}") { span ->
        // If the incoming job has an original trace id, set that as a tag on the new span.
        // We don't turn that into the parent of the current span because that would
        // incorrectly include the execution time of the job in the execution time of the
        // action that triggered the job
        firstMessage.attributes[SqsJob.ORIGINAL_TRACE_ID_ATTR]?.let {
          ORIGINAL_TRACE_ID_TAG.set(span, it)
        }

        // Run the handler and record timing
        try {
          if (useBatchHandler) {
            messages.forEachIndexed { index, message ->
              MDC.put("$SQS_JOB_IDS_STRUCTURED_MDC.$index", message.id)
            }
          } else {
            MDC.put(SQS_JOB_ID_MDC, firstMessage.id)
            MDC.put(SQS_JOB_ID_STRUCTURED_MDC, firstMessage.id)
          }
          MDC.put(SQS_QUEUE_NAME_MDC, firstMessage.queueName.value)
          MDC.put(SQS_QUEUE_TYPE_MDC, SQS_QUEUE_TYPE)
          val (duration, _) = timed {
            when(useBatchHandler) {
              true -> batchHandler!!.handleJobs(messages)
              false -> individualHandler!!.handleJob(firstMessage)
            }
          }
          metrics.handlerDispatchTime.record(
            duration.toMillis().toDouble(), queue.queueName,
            queue.queueName
          )
          Status.OK
        } catch (th: Throwable) {
          val mdcTags = TaggedLogger.popThreadLocalMdcContext()

          log.error(th, *mdcTags.toTypedArray()) { "error handling job from ${queue.queueName}" }

          metrics.handlerFailures.labels(queue.queueName, queue.queueName).inc()
          Tags.ERROR.set(span, true)
          Status.FAILED
        } finally {
          if (useBatchHandler) {
            messages.indices.forEach { index ->
              MDC.remove("$SQS_JOB_IDS_STRUCTURED_MDC.$index")
            }
          } else {
            MDC.remove(SQS_JOB_ID_MDC)
            MDC.remove(SQS_JOB_ID_STRUCTURED_MDC)
          }
          MDC.remove(SQS_QUEUE_NAME_MDC)
          MDC.remove(SQS_QUEUE_TYPE_MDC)
        }
      }
    }
}

  companion object {
    private val log = getLogger<SqsJobConsumer>()
    internal val POD_CONSUMERS_PER_QUEUE = Feature("pod-jobqueue-consumers")
    internal val POD_MAX_JOBQUEUE_CONSUMERS = Feature("pod-max-jobqueue-consumers")
    internal val CONSUMERS_PER_QUEUE = Feature("jobqueue-consumers")
    internal val CONSUMERS_BATCH_SIZE = Feature("jobqueue-consumers-fetch-batch-size")
    private val ORIGINAL_TRACE_ID_TAG = StringTag("original.trace_id")
    private const val SQS_JOB_ID_MDC = "sqs_job_id"
    private const val SQS_QUEUE_TYPE_MDC = "misk.job_queue.queue_type"
    private const val SQS_JOB_ID_STRUCTURED_MDC = "misk.job_queue.job_id"
    private const val SQS_JOB_IDS_STRUCTURED_MDC = "misk.job_queue.job_ids"
    private const val SQS_QUEUE_NAME_MDC = "misk.job_queue.queue_name"
    private const val SQS_QUEUE_TYPE = "aws-sqs"
    private const val SQS_ATTRIBUTE_SENT_TIMESTAMP = "SentTimestamp"
    private const val SQS_ATTRIBUTE_APPROX_RECEIVE_COUNT = "ApproximateReceiveCount"
  }

}
