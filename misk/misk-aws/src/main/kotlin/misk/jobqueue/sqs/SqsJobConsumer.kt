package misk.jobqueue.sqs

import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import io.opentracing.Tracer
import io.opentracing.tag.StringTag
import io.opentracing.tag.Tags
import misk.DependentService
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.logging.getLogger
import misk.time.timed
import misk.tracing.traceWithSpan
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

@Singleton
internal class SqsJobConsumer @Inject internal constructor(
  private val config: AwsSqsJobQueueConfig,
  private val queues: QueueResolver,
  @ForSqsConsumer private val dispatchThreadPool: ExecutorService,
  private val tracer: Tracer,
  private val metrics: SqsMetrics
) : AbstractIdleService(), JobConsumer, DependentService {
  override val consumedKeys: Set<Key<*>> = setOf()
  override val producedKeys: Set<Key<*>> = setOf(Key.get(SqsJobConsumer::class.java))

  private val subscriptions = ConcurrentHashMap<QueueName, JobConsumer.Subscription>()

  override fun startUp() {}

  override fun shutDown() {
    subscriptions.values.forEach { it.close() }
  }

  override fun subscribe(queueName: QueueName, handler: JobHandler): JobConsumer.Subscription {
    val receiver = QueueReceiver(queueName, handler)
    if (subscriptions.putIfAbsent(queueName, receiver) != null) {
      throw IllegalStateException("already subscribed to queue ${queueName.value}")
    }

    log.info {
      "subscribing to queue ${queueName.value} with ${config.concurrent_receivers_per_queue} receivers"
    }

    for (i in (0 until config.concurrent_receivers_per_queue)) {
      thread(name = "sqs-receiver-${queueName.value}-$i", start = true) {
        log.info { "launching receiver $i for ${queueName.value}" }
        receiver.run()
      }
    }

    return receiver
  }

  private inner class QueueReceiver(
    private val queueName: QueueName,
    private val handler: JobHandler
  ) : Runnable, JobConsumer.Subscription {
    private val queue = queues[queueName]
    private val running = AtomicBoolean(true)

    override fun run() {
      while (running.get()) {
        val messages = queue.call { client ->
          client.receiveMessage(ReceiveMessageRequest()
              .withAttributeNames("All")
              .withMessageAttributeNames("All")
              .withQueueUrl(queue.url)
              .withMaxNumberOfMessages(10))
              .messages
        }

        messages.map { SqsJob(queueName, queues, metrics, it) }.forEach { message ->
          dispatchThreadPool.submit {
            metrics.jobsReceived.labels(queueName.value).inc()

            tracer.traceWithSpan("handle-job-${queueName.value}") { span ->
              // If the incoming job has an original trace id, set that as a tag on the new span.
              // We don't turn that into the parent of the current span because that would
              // incorrectly include the execution time of the job in the execution time of the
              // action that triggered the job
              message.attributes[SqsJob.ORIGINAL_TRACE_ID_ATTR]?.let {
                ORIGINAL_TRACE_ID_TAG.set(span, it)
              }

              // Run the handler and record timing
              try {
                val (duration, _) = timed { handler.handleJob(message) }
                metrics.handlerDispatchTime.record(duration.toMillis().toDouble(), queueName.value)
              } catch (th: Throwable) {
                log.error(th) { "error handling job from ${queueName.value}" }
                metrics.handlerFailures.labels(queueName.value).inc()
                Tags.ERROR.set(span, true)
              }
            }
          }
        }
      }
    }

    override fun close() {
      if (!subscriptions.remove(queueName, this)) return

      log.info { "closing subscription to queue ${queueName.value}" }
      running.set(false)
    }
  }

  companion object {
    private val log = getLogger<SqsJobConsumer>()

    private val ORIGINAL_TRACE_ID_TAG = StringTag("original.trace_id")
  }
}
