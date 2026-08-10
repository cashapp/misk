package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractService
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import jakarta.inject.Inject
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.inject.AsyncSwitch
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobConsumer
import misk.jobqueue.v2.JobHandler
import misk.logging.getLogger
import misk.testing.TestFixture

/**
 * Instruments queue consumption.
 *
 * It runs:
 * - single coroutine for each queue, and it's retry queue to poll for messages
 * - N coroutines (configurable) for handling the messages
 *
 * Queue polling is fully suspending and runs on a dedicated single thread view of Dispatchers.IO.
 *
 * Handling coroutines run on a dedicated potentially multithreaded view of Dispatchers.IO. Each queue will get its own
 * view. It's up to the service to decide how many threads are needed for handling. If code executed by the handler uses
 * suspending APIs and is not CPU intensive, a single thread should be sufficient. If handler performs CPU intensive
 * operations or uses blocking API, it is advisable to adjust the thread count to match the needs.
 *
 * By default, polling coroutine communicates with handlers via a rendezvous channel. This effectively means that
 * polling coroutine will wait until all the jobs from the last roundtrip are picked by the handlers before sending
 * another request to SQS. Use channel with a larger buffer size to prefetch messages. This can reduce the latency, but
 * increase the risk of hitting visibility timeout.
 *
 * On shutdown, queues configured with a positive `drain_timeout_ms` are drained gracefully: polling stops issuing new
 * receive requests, jobs that were already received keep their handlers and are given up to the configured deadline to
 * finish and acknowledge, and only then is the remaining work cancelled. The service reports stopped only after every
 * queue's drain has completed or timed out. Queues without a drain timeout keep the legacy behavior of immediate
 * cancellation. Visibility is not extended while draining; the drain deadline should be lower than the queue's
 * visibility timeout.
 *
 * Stopping new receives is best effort: a receive request already in flight when the drain starts is cancelled, but SQS
 * may have already assigned messages to it server-side. Such messages were never seen by this consumer and become
 * receivable again once their visibility timeout expires. Draining removes the systematic abandonment of in-flight
 * work; it does not make shutdown atomic with respect to a racing receive.
 */
@Singleton
class SqsJobConsumer
@Inject
constructor(
  private val sqsClientFactory: SqsClientFactory,
  private val sqsQueueResolver: SqsQueueResolver,
  private val visibilityTimeoutCalculator: VisibilityTimeoutCalculator,
  private val moshi: Moshi,
  private val dlqProvider: DeadLetterQueueProvider,
  private val sqsMetrics: SqsMetrics,
  private val clock: Clock,
  private val tracer: Tracer,
  private val asyncSwitch: AsyncSwitch,
) : JobConsumer, AbstractService(), TestFixture {
  private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
  // Dedicated single-slot view so drain deadline enforcement and notifyStopped() cannot be starved by
  // other work saturating the shared IO dispatcher.
  private val stopScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

  private val subscriptions = ConcurrentHashMap<QueueName, Subscription>()

  override fun subscribe(queueName: QueueName, handler: JobHandler) {
    subscribe(queueName = queueName, handler = handler, queueConfig = SqsQueueConfig())
  }

  fun subscribe(queueName: QueueName, handler: JobHandler, queueConfig: SqsQueueConfig) {
    // We won't resolve dead letter queue yet to skip it for local development and testing
    val deadLetterQueueName = dlqProvider.deadLetterQueueFor(queueName)

    val channel = Channel<SqsJob>(queueConfig.channel_capacity)
    val subscriber =
      Subscriber(
        queueName = queueName,
        queueConfig = queueConfig,
        deadLetterQueueName = deadLetterQueueName,
        handler = handler,
        channel = channel,
        client = sqsClientFactory.get(queueConfig.region!!),
        sqsQueueResolver = sqsQueueResolver,
        sqsMetrics = sqsMetrics,
        moshi = moshi,
        clock = clock,
        tracer = tracer,
        visibilityTimeoutCalculator = visibilityTimeoutCalculator,
        asyncSwitch = asyncSwitch,
      )

    val pollJob = scope.launch { subscriber.poll() }
    val handlingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(queueConfig.parallelism) + SupervisorJob())
    val handlerJobs = List(queueConfig.concurrency) { handlingScope.launch { subscriber.run() } }
    subscriptions[queueName] =
      Subscription(
        subscriber = subscriber,
        channel = channel,
        pollJob = pollJob,
        handlingScope = handlingScope,
        handlerJobs = handlerJobs,
        queueConfig = queueConfig,
      )
  }

  override fun unsubscribe(queueName: QueueName) {
    subscriptions[queueName]?.handlingScope?.cancel()
  }

  /** Called automatically between every test to prevent long-running scopes or test timeouts. */
  override fun reset() {
    subscriptions.values.forEach { it.handlingScope.cancel() }
  }

  override fun doStart() {
    notifyStarted()
  }

  override fun doStop() {
    val subs = subscriptions.values.toList()
    // A subscription whose handling scope was already cancelled (unsubscribe/reset) has no handlers left
    // to drain; waiting on it would burn the full drain deadline for nothing.
    val (drainable, immediate) =
      subs.partition { (it.queueConfig.drain_timeout_ms ?: 0) > 0 && it.handlingScope.isActive }
    if (drainable.isEmpty()) {
      scope.cancel()
      subs.forEach { it.handlingScope.cancel() }
      notifyStopped()
      return
    }

    immediate.forEach {
      it.pollJob.cancel()
      it.handlingScope.cancel()
    }
    stopScope.launch {
      try {
        coroutineScope { drainable.forEach { subscription -> launch { drain(subscription) } } }
        scope.cancel()
        subs.forEach { it.handlingScope.cancel() }
        notifyStopped()
      } catch (t: Throwable) {
        scope.cancel()
        subs.forEach { it.handlingScope.cancel() }
        notifyFailed(t)
      }
    }
  }

  private suspend fun drain(subscription: Subscription) {
    val queueName = subscription.subscriber.queueName.value
    val drainTimeoutMs = subscription.queueConfig.drain_timeout_ms!!
    val startMs = clock.millis()

    val inFlightAtStart = subscription.subscriber.startDraining()
    sqsMetrics.drainsStarted.labels(queueName).inc()
    sqsMetrics.drainInFlightAtStart.labels(queueName).observe(inFlightAtStart.toDouble())
    logger.info {
      "Draining SQS consumer for queue $queueName: $inFlightAtStart job(s) in flight, deadline ${drainTimeoutMs}ms"
    }

    val drained =
      withTimeoutOrNull(drainTimeoutMs) {
        subscription.pollJob.join()
        subscription.channel.close()
        subscription.handlerJobs.joinAll()
      } != null

    if (drained) {
      logger.info { "Drain of queue $queueName completed in ${clock.millis() - startMs}ms" }
    } else {
      val cancelledJobs = subscription.subscriber.inFlightCount()
      sqsMetrics.drainJobsCancelled.labels(queueName).inc(cancelledJobs.toDouble())
      subscription.pollJob.cancel()
      subscription.channel.close()
      subscription.handlingScope.cancel()
      logger.warn {
        "Drain of queue $queueName timed out after ${drainTimeoutMs}ms, cancelled $cancelledJobs in-flight job(s)"
      }
    }
    val result = if (drained) DRAIN_RESULT_COMPLETED else DRAIN_RESULT_TIMEOUT
    sqsMetrics.drainDuration.labels(queueName, result).observe((clock.millis() - startMs).toDouble())
  }

  private class Subscription(
    val subscriber: Subscriber,
    val channel: Channel<SqsJob>,
    val pollJob: Job,
    val handlingScope: CoroutineScope,
    val handlerJobs: List<Job>,
    val queueConfig: SqsQueueConfig,
  )

  companion object {
    private val logger = getLogger<SqsJobConsumer>()
    internal const val DRAIN_RESULT_COMPLETED = "completed"
    internal const val DRAIN_RESULT_TIMEOUT = "timeout"
  }
}
