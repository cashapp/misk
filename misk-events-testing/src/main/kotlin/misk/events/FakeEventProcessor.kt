package misk.events

import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An in-memory events system for testing publishers and consumers.
 *
 * To use this, first install the [FakeEventProcessorModule] in your program's test module:
 *
 * ```
 * install(FakeEventProcessorModule)
 * ```
 *
 * Next, use Guice multibindings to register consumers. You can use the same registrations for both
 * test and production code.
 *
 * ```
 * newMapBinder<Topic, Consumer.Handler>().addBinding(MY_TOPIC).to<MyConsumer>()
 * ```
 *
 * To publish, inject a [Producer] and call [publish].
 *
 * To consume all published events, call [deliverAll]. All events will be delivered events to the
 * corresponding consumers. Note that no call to [Consumer.subscribe] is necessary.
 */
@Singleton
@Deprecated("This API is no longer supported and replaced by the new event system's client library")
class FakeEventProcessor @Inject constructor(
  private val consumers: Map<Topic, Consumer.Handler>
) : Producer {
  /** Events published and not yet consumed. */
  val queue: BlockingDeque<PublishedEvent> = LinkedBlockingDeque()

  /** Events scheduled for retry. */
  val retryQueue: BlockingDeque<PublishedEvent> = LinkedBlockingDeque()

  /** Events published with no consumer registered. */
  val droppedQueue: BlockingDeque<PublishedEvent> = LinkedBlockingDeque()

  /** Handle unhandled events by adding them to the dropped queue. */
  private val defaultConsumer = object : Consumer.Handler {
    override fun handleEvents(ctx: Consumer.Context, vararg events: Event) {
      droppedQueue += events.map { PublishedEvent(ctx.topic, it) }
    }
  }

  override fun publish(topic: Topic, vararg events: Event) {
    for (event in events) {
      queue.addLast(PublishedEvent(topic, event))
    }
  }

  fun deliverAll(batchSize: Int = 100) {
    deliverAll(batchSize, queue, isRetry = false)
  }

  fun deliverRetries(batchSize: Int = 100) {
    deliverAll(batchSize, retryQueue, isRetry = true)
  }

  private fun deliverAll(batchSize: Int, queue: BlockingDeque<PublishedEvent>, isRetry: Boolean) {
    while (true) {
      val batch = queue.pollBatch(batchSize, isRetry) ?: break
      val consumer = consumers[batch.topic] ?: defaultConsumer
      consumer.handleEvents(batch, *batch.events.toTypedArray())
    }
  }

  private fun BlockingDeque<PublishedEvent>.pollBatch(batchSize: Int, isRetry: Boolean): Batch? {
    require(batchSize >= 1)

    val first = poll() ?: return null
    val topic = first.topic
    val events = mutableListOf(first.event)

    val i = iterator()
    while (events.size < batchSize && i.hasNext()) {
      val candidate = i.next()
      if (candidate.topic == topic) {
        i.remove()
        events += candidate.event
      }
    }

    return Batch(topic, events, isRetry)
  }

  data class PublishedEvent(
    val topic: Topic,
    val event: Event,
  )

  private inner class Batch(
    override val topic: Topic,
    val events: List<Event>,
    override val isRetry: Boolean
  ) : Consumer.Context {
    override fun retryLater(vararg events: Event) {
      for (event in events) {
        retryQueue += PublishedEvent(topic, event)
      }
    }
  }
}
