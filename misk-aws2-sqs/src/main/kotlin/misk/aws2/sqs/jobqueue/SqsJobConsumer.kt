package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractService
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import misk.aws2.sqs.jobqueue.config.SqsQueueConfig
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobConsumer
import misk.jobqueue.v2.JobHandler
import wisp.logging.getLogger
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Instruments queue consumption.
 *
 * It runs:
 * - single coroutine for each queue, and it's retry queue to poll for messages
 * - N coroutines (configurable) for handling the messages
 *
 * Queue polling is fully suspending and runs on a dedicated single thread view of Dispatchers.IO.
 *
 * Handling coroutines run on a dedicated potentially multithreaded view of Dispatchers.IO.
 * Each queue will get its own view. It's
 * up to the service to decide how many threads are needed for handling. If code executed by the
 * handler uses suspending APIs and is not CPU intensive, a single thread should be sufficient.
 * If handler performs CPU intensive operations or uses blocking API, it is advisable to adjust
 * the thread count to match the needs.
 *
 * By default, polling coroutine communicates with handlers via a rendezvous channel. This effectively
 * means that polling coroutine will wait until all the jobs from the last roundtrip are picked by
 * the handlers before sending another request to SQS. Use channel with a larger buffer size to
 * prefetch messages. This can reduce the latency, but increase the risk of hitting visibility timeout.
 */
@Singleton
class SqsJobConsumer @Inject constructor(
  private val sqsClientFactory: SqsClientFactory,
  private val sqsQueueResolver: SqsQueueResolver,
  private val visibilityTimeoutCalculator: VisibilityTimeoutCalculator,
  private val moshi: Moshi,
  private val dlqProvider: DeadLetterQueueProvider,
  private val sqsMetrics: SqsMetrics,
  private val clock: Clock,
  private val tracer: Tracer,
) : JobConsumer, AbstractService() {
  private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

  private val handlingScopes = ConcurrentHashMap<QueueName, CoroutineScope>()

  override fun subscribe(queueName: QueueName, handler: JobHandler) {
    subscribe(queueName = queueName, handler = handler, queueConfig = SqsQueueConfig())
  }

  fun subscribe(queueName: QueueName, handler: JobHandler, queueConfig: SqsQueueConfig) {
    // We won't resolve dead letter queue yet to skip it for local development and testing
    val deadLetterQueueName = dlqProvider.deadLetterQueueFor(queueName)

    val subscriber = Subscriber(
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
    )

    scope.launch { subscriber.poll() }
    handlingScopes[queueName] =
      CoroutineScope(Dispatchers.IO.limitedParallelism(queueConfig.parallelism) + SupervisorJob())
    repeat(queueConfig.concurrency) {
      handlingScopes[queueName]?.launch { subscriber.run() }
    }
  }

  override fun unsubscribe(queueName: QueueName) {
    handlingScopes[queueName]?.cancel()
  }

  override fun doStart() {
    notifyStarted()
  }

  override fun doStop() {
    scope.cancel()
    handlingScopes.values.forEach { it.cancel() }
    notifyStopped()
  }

  companion object {
    val logger = getLogger<SqsJobConsumer>()
  }
}
