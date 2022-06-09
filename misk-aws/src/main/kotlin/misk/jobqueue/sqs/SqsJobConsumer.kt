package misk.jobqueue.sqs

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.common.util.concurrent.ServiceManager
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags
import misk.feature.Feature
import misk.feature.FeatureFlags
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import misk.time.timed
import misk.tracing.traceWithNewRootSpan
import org.slf4j.MDC
import wisp.lease.LeaseManager
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
internal class SqsJobConsumer @Inject internal constructor(
  @ForSqsHandling private val handlingThreads: ExecutorService,
  @ForSqsHandling private val taskQueue: RepeatedTaskQueue,
  @ForSqsReceiving private val receivingThreads: ExecutorService,
  private val featureFlags: FeatureFlags,
  private val leaseManager: LeaseManager,
  private val metrics: SqsMetrics,
  private val moshi: Moshi,
  private val queues: QueueResolver,
  private val serviceManagerProvider: Provider<ServiceManager>,
  private val tracer: Tracer,
) : JobConsumer {
  private val subscriptions = ConcurrentHashMap<QueueName, QueueReceiver>()

  override fun subscribe(queueName: QueueName, handler: JobHandler) {
    val receiver = QueueReceiver(queueName, handler)
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

  internal fun getReceiver(queueName: QueueName): QueueReceiver {
    return subscriptions[queueName]!!
  }

  internal inner class QueueReceiver(
    queueName: QueueName,
    private val handler: JobHandler
  ) {
    private val queue = queues.getForReceiving(queueName)

    fun run(): Status {
      // Receive messages in parallel. Default to 1 if feature flag is not defined.
      val futures = receiverIds().map {
        CompletableFuture.supplyAsync(
          {
            receive()
          },
          receivingThreads
        )
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

    /**
     * List containing arbitrary numbers. The size of the list is the number of receivers to use.
     */
    private fun receiverIds(): List<Int> {
      val numReceiversPerPodForQueue = receiversPerPodForQueue()
      val shouldUsePerPodConfig = numReceiversPerPodForQueue >= 0
      if (shouldUsePerPodConfig) {
        return (1..numReceiversPerPodForQueue).toList()
      }
      return (1..receiversForQueue())
        .filter { num ->
          val lease = leaseManager.requestLease("sqs-job-consumer-${queue.name.value}-$num")
          if (!lease.checkHeld()) {
            lease.acquire()
          } else {
            true
          }
        }
    }

    private fun receiversPerPodForQueue(): Int {
      return featureFlags.getInt(POD_CONSUMERS_PER_QUEUE, queue.queueName)
    }

    private fun receiversForQueue(): Int {
      return featureFlags.getInt(CONSUMERS_PER_QUEUE, queue.queueName)
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
      return messages.map { message ->
        CompletableFuture.supplyAsync(
          {
            metrics.jobsReceived.labels(queue.queueName, queue.queueName).inc()

            tracer.traceWithNewRootSpan("handle-job-${queue.queueName}") { span ->
              // If the incoming job has an original trace id, set that as a tag on the new span.
              // We don't turn that into the parent of the current span because that would
              // incorrectly include the execution time of the job in the execution time of the
              // action that triggered the job
              message.attributes[SqsJob.ORIGINAL_TRACE_ID_ATTR]?.let {
                ORIGINAL_TRACE_ID_TAG.set(span, it)
              }

              // Run the handler and record timing
              try {
                MDC.put(SQS_JOB_ID_MDC, message.id)
                val (duration, _) = timed { handler.handleJob(message) }
                metrics.handlerDispatchTime.record(
                  duration.toMillis().toDouble(), queue.queueName,
                  queue.queueName
                )
                Status.OK
              } catch (th: Throwable) {
                log.error(th) { "error handling job from ${queue.queueName}" }
                metrics.handlerFailures.labels(queue.queueName, queue.queueName).inc()
                Tags.ERROR.set(span, true)
                Status.FAILED
              } finally {
                MDC.remove(SQS_JOB_ID_MDC)
              }
            }
          },
          handlingThreads
        )
      }
    }
  }

  companion object {
    private val log = getLogger<SqsJobConsumer>()
    internal val POD_CONSUMERS_PER_QUEUE = Feature("pod-jobqueue-consumers")
    internal val CONSUMERS_PER_QUEUE = Feature("jobqueue-consumers")
    internal val CONSUMERS_BATCH_SIZE = Feature("jobqueue-consumers-fetch-batch-size")
    private val ORIGINAL_TRACE_ID_TAG = StringTag("original.trace_id")
    private const val SQS_JOB_ID_MDC = "sqs_job_id"
  }
}
