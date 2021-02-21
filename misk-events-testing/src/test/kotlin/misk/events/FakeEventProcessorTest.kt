package misk.events

import misk.events.FakeEventProcessor.PublishedEvent
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.test.assertFailsWith

@MiskTest
class FakeEventProcessorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var producer: Producer
  @Inject lateinit var fakeEventProcessor: FakeEventProcessor
  @Inject lateinit var simpleConsumer: SimpleConsumer
  @Inject lateinit var retryOnceConsumer: RetryOnceConsumer

  @Test
  fun produceAndConsume() {
    val event = newEvent("a")

    producer.publish(SIMPLE_TOPIC, event)
    assertThat(fakeEventProcessor.queue).hasSize(1)
    assertThat(simpleConsumer.receivedEvents).isEmpty()
    assertThat(retryOnceConsumer.receivedEvents).isEmpty()

    fakeEventProcessor.deliverAll()
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(simpleConsumer.receivedEvents).containsExactly(event)
    assertThat(retryOnceConsumer.receivedEvents).isEmpty()
  }

  @Test
  fun retriesThrowWithoutOptIn() {
    val event = newEvent("a")

    producer.publish(RETRY_ONCE_TOPIC, event)
    val e = assertFailsWith<IllegalStateException> {
      fakeEventProcessor.deliverAll()
    }
    assertThat(e).hasMessage("unexpected retry! use allowRetries=true if that is expected")
  }

  @Test
  fun retriedEvents() {
    val event = newEvent("a")

    producer.publish(RETRY_ONCE_TOPIC, event)
    assertThat(fakeEventProcessor.queue).hasSize(1)
    assertThat(fakeEventProcessor.retryQueue).hasSize(0)

    fakeEventProcessor.deliverAll(allowRetries = true)
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(fakeEventProcessor.retryQueue).hasSize(1)
    assertThat(simpleConsumer.receivedEvents).isEmpty()
    assertThat(retryOnceConsumer.receivedEvents).containsExactly(event)

    fakeEventProcessor.deliverAll(allowRetries = false)
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(fakeEventProcessor.retryQueue).hasSize(0)
    assertThat(simpleConsumer.receivedEvents).isEmpty()
    assertThat(retryOnceConsumer.receivedEvents).containsExactly(event, event)
  }

  @Test
  fun retriesAreAttemptedOnlyOnce() {
    val event = newEvent("a")

    producer.publish(RETRY_ALWAYS_TOPIC, event)
    assertThat(fakeEventProcessor.queue).hasSize(1)
    assertThat(fakeEventProcessor.retryQueue).hasSize(0)

    fakeEventProcessor.deliverAll(allowRetries = true)
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(fakeEventProcessor.retryQueue).hasSize(1)

    fakeEventProcessor.deliverAll(allowRetries = true)
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(fakeEventProcessor.retryQueue).hasSize(1)
  }

  @Test
  fun unconsumedEvents() {
    val event = newEvent("a")

    producer.publish(NO_CONSUMER_TOPIC, event)
    assertThat(fakeEventProcessor.queue).hasSize(1)
    assertThat(fakeEventProcessor.droppedQueue).hasSize(0)

    fakeEventProcessor.deliverAll()
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(fakeEventProcessor.droppedQueue)
      .containsExactly(PublishedEvent(NO_CONSUMER_TOPIC, event, isRetry = false))
  }

  @Test
  fun batches() {
    producer.publish(SIMPLE_TOPIC, newEvent("a"), newEvent("b"), newEvent("c"), newEvent("d"))

    fakeEventProcessor.deliverAll(batchSize = 3)
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(simpleConsumer.receivedEvents)
      .containsExactly(newEvent("a"), newEvent("b"), newEvent("c"), newEvent("d"))
    assertThat(simpleConsumer.batchCount).isEqualTo(2)
  }

  @Test
  fun `interleaved batches`() {
    producer.publish(SIMPLE_TOPIC, newEvent("a"))
    producer.publish(RETRY_ONCE_TOPIC, newEvent("b"))
    producer.publish(SIMPLE_TOPIC, newEvent("c"))
    producer.publish(RETRY_ONCE_TOPIC, newEvent("d"))
    producer.publish(SIMPLE_TOPIC, newEvent("e"))

    fakeEventProcessor.deliverAll(batchSize = 3, allowRetries = true)
    assertThat(fakeEventProcessor.queue).hasSize(0)
    assertThat(simpleConsumer.receivedEvents)
      .containsExactly(newEvent("a"), newEvent("c"), newEvent("e"))
    assertThat(simpleConsumer.batchCount).isEqualTo(1)
    assertThat(retryOnceConsumer.receivedEvents)
      .containsExactly(newEvent("b"), newEvent("d"))
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(FakeEventProcessorModule)
      newMapBinder<Topic, Consumer.Handler>()
        .addBinding(SIMPLE_TOPIC)
        .to<SimpleConsumer>()
      newMapBinder<Topic, Consumer.Handler>()
        .addBinding(RETRY_ONCE_TOPIC)
        .to<RetryOnceConsumer>()
      newMapBinder<Topic, Consumer.Handler>()
        .addBinding(RETRY_ALWAYS_TOPIC)
        .to<RetryAlwaysConsumer>()
    }
  }

  @Singleton
  class SimpleConsumer @Inject constructor() : Consumer.Handler {
    var batchCount = 0
    val receivedEvents = mutableListOf<Event>()

    override fun handleEvents(ctx: Consumer.Context, vararg events: Event) {
      batchCount++
      receivedEvents += events.toList()
    }
  }

  @Singleton
  class RetryOnceConsumer @Inject constructor() : Consumer.Handler {
    val receivedEvents = mutableListOf<Event>()

    override fun handleEvents(ctx: Consumer.Context, vararg events: Event) {
      receivedEvents += events.toList()
      if (!ctx.isRetry) {
        ctx.retryLater(*events)
      }
    }
  }

  @Singleton
  class RetryAlwaysConsumer @Inject constructor() : Consumer.Handler {
    override fun handleEvents(ctx: Consumer.Context, vararg events: Event) {
      ctx.retryLater(*events)
    }
  }

  private fun newEvent(body: String): Event {
    return Event(
      type = "TEST",
      body = body.encodeUtf8(),
      occurredAt = Instant.EPOCH,
      id = "1".encodeUtf8()
    )
  }

  companion object {
    val SIMPLE_TOPIC = Topic("SIMPLE")
    val RETRY_ONCE_TOPIC = Topic("RETRY_ONCE")
    val RETRY_ALWAYS_TOPIC = Topic("RETRY_ALWAYS")
    val NO_CONSUMER_TOPIC = Topic("NO_CONSUMER")
  }
}
