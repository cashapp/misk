package misk.events

/** A [Consumer] allows applications to receive events from a source */
interface Consumer {
  /** The [Context] provides information about a set of events being consumed */
  interface Context {
    /** the topic from which events are being received */
    val topic: Topic

    /** if true, the batch is being retried */
    val isRetry: Boolean

    /** Defers processing a set of events to a later time, typically placing them on a retry queue */
    fun retryLater(vararg events: Event)
  }

  /** A [Handler] handles incoming events from a topic */
  interface Handler {
    fun handleEvents(ctx: Context, vararg events: Event)
  }

  /** listens for incoming events to a topic */
  fun subscribe(topic: Topic, handler: Handler)
}

/** listens for incoming events, dispatching them to a handler function */
inline fun Consumer.subscribe(
  topic: Topic,
  crossinline handler: (Consumer.Context, List<Event>) -> Unit
) {
  subscribe(topic, object : Consumer.Handler {
    override fun handleEvents(ctx: Consumer.Context, vararg events: Event) {
      handler(ctx, events.toList())
    }
  })
}
