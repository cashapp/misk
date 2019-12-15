package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.common.util.concurrent.ServiceManager
import io.opentracing.Tracer
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.logging.getLogger
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import misk.time.timed
import misk.tracing.traceWithSpan
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
internal class SqsJobConsumer @Inject internal constructor(
  private val config: AwsSqsJobQueueConfig,
  private val queues: QueueResolver,
  @ForSqsConsumer private val dispatchThreadPool: ExecutorService,
  @ForSqsConsumer private val taskQueue: RepeatedTaskQueue,
  private val tracer: Tracer,
  private val metrics: SqsMetrics,
  /**
   * [SqsJobConsumer] is itself a [Service], but it needs the [ServiceManager] in order to check
   * that all services are running and the system is in a healthy state before it starts handling
   * jobs. We use a provider here to avoid a dependency cycle.
   */
  private val serviceManagerProvider: Provider<ServiceManager>
) : JobConsumer {
  private val subscriptions = ConcurrentHashMap<QueueName, QueueReceiver>()

  override fun subscribe(queueNames: List<QueueName>, handler: JobHandler) {
    val receiver = QueueReceiver(queueNames, handler)
    queueNames.forEach { subscribe(it, receiver) }
  }

  private fun subscribe(queueName: QueueName, receiver: QueueReceiver) {
    check(subscriptions.putIfAbsent(queueName, receiver) == null) {
      "already subscribed to queue ${queueName.value}"
    }

    log.info {
      "subscribing to queue ${queueName.value} with ${config.concurrent_receivers_per_queue} receivers"
    }

    for (i in (0 until config.concurrent_receivers_per_queue)) {
      taskQueue.scheduleWithBackoff(Duration.ZERO) {
        // Don't call handlers until all services are ready, otherwise handlers will crash because
        // the services they might need (databases, etc.) won't be ready.
        serviceManagerProvider.get().awaitHealthy()
        if (receiver.runOnce()) Status.OK else Status.NO_WORK
      }
    }
  }

  internal fun getReceiver(queueName: QueueName): QueueReceiver {
    return subscriptions[queueName]!!
  }

  internal inner class QueueReceiver(
    queueNames: List<QueueName>,
    private val handler: JobHandler
  ) {
    private val prioritizedQueues = queueNames.map { queues[it] }
    private val availableThreadSemaphore: Semaphore = Semaphore(config.consumer_thread_pool_size)

    /**
     * Pulls a batch of messages from one of the queues (in priority order), and process them using
     * the thread pool. This method does not block waiting for the tasks to complete processing.
     *
     * @return true if there were messages to be processed.
     */
    fun runOnce(): Boolean {
      prioritizedQueues.forEach { resolvedQueue ->
        if (runOnce(resolvedQueue)) {
          return@runOnce true
        }
      }
      return false
    }

    private fun runOnce(queue: ResolvedQueue): Boolean {
      val (receiveDuration, messages) = queue.call { client ->
        val receiveRequest = ReceiveMessageRequest()
          .withAttributeNames("All")
          .withMessageAttributeNames("All")
          .withQueueUrl(queue.url)
          .withMaxNumberOfMessages(10)

        timed { client.receiveMessage(receiveRequest).messages }
      }

      val queueName = queue.name

      metrics.sqsReceiveTime.record(receiveDuration.toMillis().toDouble(), queueName.value, queueName.value)

      if (messages.size == 0) {
        return false
      }

      messages.map { SqsJob(queueName, queues, metrics, it) }.forEach { message ->
        availableThreadSemaphore.acquire()
        try {
          dispatchThreadPool.submit {
            metrics.jobsReceived.labels(queueName.value, queueName.value).inc()

            tracer.traceWithSpan("handle-job-${queueName.value}") { span ->
              // If the incoming job has an original trace id, set that as a tag on the new span.
              // We don't turn that into the parent of the current span because that would
              // incorrectly include the execution time of the job in the execution time of the
              // action that triggered the job        q
              message.attributes[SqsJob.ORIGINAL_TRACE_ID_ATTR]?.let {
                ORIGINAL_TRACE_ID_TAG.set(span, it)
              }

              // Run the handler and record timing
              try {
                val (duration, _) = timed { handler.handleJob(message) }
                metrics.handlerDispatchTime.record(duration.toMillis().toDouble(), queueName.value,
                    queueName.value)
              } catch (th: Throwable) {
                log.error(th) { "error handling job from ${queueName.value}" }
                metrics.handlerFailures.labels(queueName.value, queueName.value).inc()
                Tags.ERROR.set(span, true)
                throw th
              }
            }
          }
        } finally {
          availableThreadSemaphore.release()
        }
      }
      return true
    }
  }

  companion object {
    private val log = getLogger<SqsJobConsumer>()

    private val ORIGINAL_TRACE_ID_TAG = StringTag("original.trace_id")
  }
}
