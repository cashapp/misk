package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.Service
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
 * On shutdown, queues configured with a positive `drain_timeout_ms` are drained instead of cancelled: their polling
 * loops stop issuing new receives, the jobs already handed to the handlers get up to the deadline to finish, and only
 * work still unfinished at the deadline is cancelled. Queues without the setting keep the legacy immediate
 * cancellation.
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

  private val subscriptions = ConcurrentHashMap<QueueName, Subscription>()

  override fun subscribe(queueName: QueueName, handler: JobHandler) {
    subscribe(queueName = queueName, handler = handler, queueConfig = SqsQueueConfig())
  }

  fun subscribe(queueName: QueueName, handler: JobHandler, queueConfig: SqsQueueConfig) {
    // A subscription created after shutdown began would be invisible to doStop() and outlive the service.
    val state = state()
    check(state != Service.State.STOPPING && state != Service.State.TERMINATED && state != Service.State.FAILED) {
      "Cannot subscribe to queue ${queueName.value}: SqsJobConsumer is $state"
    }

    // We won't resolve dead letter queue yet to skip it for local development and testing
    val deadLetterQueueName = dlqProvider.deadLetterQueueFor(queueName)

    val subscriber =
      Subscriber(
        queueName = queueName,
        queueConfig = queueConfig,
        deadLetterQueueName = deadLetterQueueName,
        handler = handler,
        channel = Channel(queueConfig.channel_capacity),
        client = sqsClientFactory.get(queueConfig.region!!),
        sqsQueueResolver = sqsQueueResolver,
        sqsMetrics = sqsMetrics,
        moshi = moshi,
        clock = clock,
        tracer = tracer,
        visibilityTimeoutCalculator = visibilityTimeoutCalculator,
        asyncSwitch = asyncSwitch,
      )

    val pollingJob = scope.launch { subscriber.poll() }
    val handlingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(queueConfig.parallelism) + SupervisorJob())
    val handlingJobs = List(queueConfig.concurrency) { handlingScope.launch { subscriber.run() } }
    subscriptions[queueName] = Subscription(subscriber, pollingJob, handlingScope, handlingJobs)
  }

  override fun unsubscribe(queueName: QueueName) {
    subscriptions[queueName]?.handlingScope?.cancel()
  }

  /** Called automatically between every test to prevent long-running scopes or test timeouts. */
  override fun reset() {
    subscriptions.forEach { _, subscription -> subscription.handlingScope.cancel() }
  }

  override fun doStart() {
    notifyStarted()
  }

  override fun doStop() {
    val (draining, immediate) = subscriptions.values.partition { it.isDrainable }
    immediate.forEach {
      it.pollingJob.cancel()
      it.handlingScope.cancel()
    }
    if (draining.isEmpty()) {
      scope.cancel()
      notifyStopped()
      return
    }

    draining.forEach { it.subscriber.stopPolling() }
    // The drain waits on the polling scope's own children, so it runs on a scope of its own.
    CoroutineScope(Dispatchers.IO).launch {
      try {
        draining
          .map { subscription ->
            launch {
              val timeoutMs = subscription.subscriber.queueConfig.drain_timeout_ms!!
              val drained =
                withTimeoutOrNull(timeoutMs) {
                  subscription.pollingJob.join()
                  subscription.handlingJobs.joinAll()
                }
              if (drained == null) {
                logger.warn {
                  "Queue ${subscription.subscriber.queueName.value} did not drain within ${timeoutMs}ms, " +
                    "cancelling its remaining work"
                }
              }
              subscription.pollingJob.cancel()
              subscription.handlingScope.cancel()
            }
          }
          .joinAll()
      } finally {
        scope.cancel()
        notifyStopped()
      }
    }
  }

  private class Subscription(
    val subscriber: Subscriber,
    val pollingJob: Job,
    val handlingScope: CoroutineScope,
    val handlingJobs: List<Job>,
  ) {
    /** An unsubscribed queue's handlers are already cancelled, there is nothing left to drain. */
    val isDrainable: Boolean
      get() = (subscriber.queueConfig.drain_timeout_ms ?: 0) > 0 && handlingScope.isActive
  }

  companion object {
    private val logger = getLogger<SqsJobConsumer>()
  }
}
