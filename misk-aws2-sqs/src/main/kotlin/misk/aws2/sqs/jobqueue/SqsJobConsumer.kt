package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractService
import com.google.inject.Singleton
import com.squareup.moshi.Moshi
import io.opentracing.Tracer
import jakarta.inject.Inject
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
 * It runs one or more consumers for each queue. Each consumer has:
 * - one coroutine polling the queue and its retry queue for messages
 * - N coroutines processing messages from that queue
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
  private val consumptionControllers: Map<QueueName, SqsConsumptionController>,
) : JobConsumer, AbstractService(), TestFixture {
  private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

  private val subscriptions = ConcurrentHashMap<QueueName, Subscription>()

  override fun subscribe(queueName: QueueName, handler: JobHandler) {
    subscribe(queueName = queueName, handler = handler, queueConfig = SqsQueueConfig())
  }

  fun subscribe(queueName: QueueName, handler: JobHandler, queueConfig: SqsQueueConfig) {
    val consumptionController =
      consumptionControllers[queueName]
        ?: StaticSqsConsumptionController(queueConfig.concurrency)
    subscribeWithController(queueName, handler, queueConfig, consumptionController)
  }

  private fun subscribeWithController(
    queueName: QueueName,
    handler: JobHandler,
    queueConfig: SqsQueueConfig,
    consumptionController: SqsConsumptionController,
  ) {
    stopSubscription(subscriptions.remove(queueName))

    val deadLetterQueueName = dlqProvider.deadLetterQueueFor(queueName)
    val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val controllerJob = controllerScope.launch {
      runSubscription(queueName, queueConfig, deadLetterQueueName, handler, consumptionController)
    }

    subscriptions[queueName] = ActiveSubscription(controllerScope, controllerJob)
  }

  override fun unsubscribe(queueName: QueueName) {
    stopSubscription(subscriptions.remove(queueName))
  }

  override fun reset() {
    stopAllSubscriptions()
  }

  override fun doStart() {
    notifyStarted()
  }

  override fun doStop() {
    val subscriptionsToStop = subscriptions.values.toList()
    subscriptions.clear()
    scope.cancel()
    runBlocking { subscriptionsToStop.forEach { it.stop() } }
    notifyStopped()
  }

  private suspend fun runSubscription(
    queueName: QueueName,
    queueConfig: SqsQueueConfig,
    deadLetterQueueName: QueueName,
    handler: JobHandler,
    controller: SqsConsumptionController,
  ) {
    val activeLanes = linkedMapOf<String, ConsumptionLane>()
    val refreshInterval = queueConfig.slot_refresh_interval_ms.milliseconds
    val drainTimeout = queueConfig.slot_drain_timeout_ms.milliseconds
    val handlingScope = CoroutineScope(Dispatchers.IO.limitedParallelism(queueConfig.parallelism) + SupervisorJob())
    try {
      while (currentCoroutineContext().isActive) {
        val slots =
          try {
            controller.acquireSlots()
          } catch (e: Exception) {
            logger.warn(e) { "Failed to acquire SQS consumption slots for queue ${queueName.value}" }
            stopLanes(
              activeLanes,
              activeLanes.filter { !it.value.isHeld() }.keys.toList(),
              drainTimeout,
            )
            delay(refreshInterval)
            continue
          }

        val handlersPerSlot = controller.handlersPerSlot
        if (handlersPerSlot <= 0) {
          stopLanes(activeLanes, activeLanes.keys.toList(), drainTimeout)
          delay(refreshInterval)
          continue
        }

        val desiredSlots = slots.filterValues { it.isHeld() }
        val slotsToStop =
          activeLanes
            .filter { (id, activeLane) ->
              id !in desiredSlots || !activeLane.isHeld() || activeLane.handlersPerSlot != handlersPerSlot
            }
            .keys
            .toList()

        stopLanes(activeLanes, slotsToStop, drainTimeout)

        desiredSlots.forEach { (id, slot) ->
          if (id !in activeLanes) {
            activeLanes[id] =
              startLane(
                queueName = queueName,
                queueConfig = queueConfig,
                deadLetterQueueName = deadLetterQueueName,
                handler = handler,
                handlersPerSlot = handlersPerSlot,
                slot = slot,
                handlingScope = handlingScope,
              )
          }
        }

        delay(refreshInterval)
      }
    } finally {
      withContext(NonCancellable) {
        stopLanes(activeLanes, activeLanes.keys.toList(), drainTimeout)
        handlingScope.cancel()
      }
    }
  }

  private fun startLane(
    queueName: QueueName,
    queueConfig: SqsQueueConfig,
    deadLetterQueueName: QueueName,
    handler: JobHandler,
    handlersPerSlot: Int,
    slot: SqsConsumerSlot?,
    handlingScope: CoroutineScope,
  ): ConsumptionLane {
    val subscriber = newSubscriber(queueName, queueConfig, deadLetterQueueName, handler)
    val pollJob = scope.launch { subscriber.pollWhile { slot?.isHeld() != false } }
    val handlerJobs = List(handlersPerSlot) { handlingScope.launch { subscriber.run() } }
    return ConsumptionLane(slot, subscriber.channel, pollJob, handlerJobs, handlersPerSlot)
  }

  private suspend fun stopLanes(
    activeLanes: MutableMap<String, ConsumptionLane>,
    laneIds: List<String>,
    drainTimeout: Duration,
  ) {
    laneIds.forEach { id ->
      activeLanes.remove(id)?.stop(drainTimeout)
    }
  }

  private fun stopSubscription(subscription: Subscription?) {
    if (subscription == null) return
    runBlocking { subscription.stop() }
  }

  private fun stopAllSubscriptions() {
    val subscriptionsToStop = subscriptions.values.toList()
    subscriptions.clear()
    runBlocking { subscriptionsToStop.forEach { it.stop() } }
  }

  private fun newSubscriber(
    queueName: QueueName,
    queueConfig: SqsQueueConfig,
    deadLetterQueueName: QueueName,
    handler: JobHandler,
  ): Subscriber {
    return Subscriber(
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
  }

  private interface Subscription {
    suspend fun stop()
  }

  private data class ActiveSubscription(
    private val scope: CoroutineScope,
    private val controllerJob: Job,
  ) : Subscription {
    override suspend fun stop() {
      controllerJob.cancelAndJoin()
      scope.cancel()
    }
  }

  private data class ConsumptionLane(
    private val slot: SqsConsumerSlot?,
    private val channel: Channel<SqsJob>,
    private val pollJob: Job,
    private val handlerJobs: List<Job>,
    val handlersPerSlot: Int,
  ) {
    fun isHeld(): Boolean = slot?.isHeld() != false

    suspend fun stop(drainTimeout: Duration) {
      pollJob.cancelAndJoin()
      channel.close()

      val drained =
        withTimeoutOrNull(drainTimeout) {
          handlerJobs.joinAll()
          true
        } == true
      if (!drained) {
        handlerJobs.forEach { it.cancel() }
      }

      try {
        slot?.release()
      } catch (e: Exception) {
        logger.warn(e) { "Failed to release SQS consumption slot" }
      }
    }
  }

  private data class StaticSqsConsumptionController(
    override val handlersPerSlot: Int,
  ) : SqsConsumptionController {
    override suspend fun acquireSlots(): Map<String, SqsConsumerSlot> {
      return mapOf(STATIC_SLOT_ID to StaticSqsConsumerSlot)
    }
  }

  private object StaticSqsConsumerSlot : SqsConsumerSlot {
    override fun isHeld(): Boolean = true

    override fun release() {}
  }

  companion object {
    private val logger = getLogger<SqsJobConsumer>()
    private const val STATIC_SLOT_ID = "static"
  }
}
