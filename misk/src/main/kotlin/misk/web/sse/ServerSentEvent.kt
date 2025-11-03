package misk.web.sse

/**
 * Represents a Server-Sent Event (SSE) as defined in the
 * [SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation).
 *
 * Server-Sent Events enable servers to push data to web clients over HTTP. Each event is sent as a
 * block of text terminated by a pair of newlines. The fields of an event are sent as lines of text,
 * with each line consisting of a field name followed by a colon and the field value.
 *
 * @property data The data field for the event. This is the main payload of the event. When serialized,
 * if the data contains newlines, it will be split into multiple `data:` lines as per the
 * [SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation).
 * Can be any type that can be serialized to a string representation.
 *
 * @property event The event type. If specified, the event will be dispatched on the browser to a
 * listener for the specified event name. If not specified, the event will be dispatched to the
 * generic `onmessage` handler. See the
 * [event field specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation).
 *
 * @property id The event ID to set the last event ID value. The browser will send this ID in the
 * `Last-Event-ID` header when reconnecting, allowing the server to resume the event stream. See the
 * [id field specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation).
 *
 * @property retry The reconnection time in milliseconds. If specified, the browser will wait this
 * amount of time before attempting to reconnect when the connection is lost. Must be a non-negative
 * integer. See the
 * [retry field specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation).
 *
 * @property comments Comment lines to include in the event. Comments are sent as lines beginning with
 * a colon. They are ignored by the browser but can be useful for debugging or keeping the connection
 * alive. See the
 * [comment specification](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation).
 */
data class ServerSentEvent @JvmOverloads constructor(
  val data: String? = null,
  val event: String? = null,
  val id: String? = null,
  val retry: Long? = null,
  val comments: String? = null
)


const val LAST_EVENT_ID_HEADER = "Last-Event-ID"




