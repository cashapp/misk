package misk.events

/**
 * A [Producer] is used to send events to an event stream.
 */
@Deprecated("This API is no longer supported and replaced by the new event system's client library")
interface Producer {
  /**
   * Publishes an event to an event stream.
   *
   * If multiple events need to be published atomically, or if end-to-end ordering is required, use a
   * [SpooledProducer].
   */
  fun publish(topic: Topic, vararg events: Event)
}
