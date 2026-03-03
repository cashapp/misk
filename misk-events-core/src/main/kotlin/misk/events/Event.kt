package misk.events

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.time.Instant

data class Event(
  /** The type of event */
  val type: String,

  /** the content so the event, encoded as a protobuf */
  val body: ByteString,

  /** the instant at which the event occurred */
  val occurredAt: Instant,

  /** a global unique id for the event */
  val id: ByteString,

  /**
   * Events often represent a stream of state changes or entity updates; the update_version
   * can be used to indicate the version of the updated entity or state machine at the time
   * the event was generated. Legacy events may not have this field populated. New events
   * must specify this for true ordering and race prevention.
   */
  val updateVersion: Long? = null,

  /**
   * The id of the entity to which the event is referencing. Many but not all events
   * are correlated with a specific entity; if this event is related to an entity,
   * the entity_identifier should specify the id of that entity
   */
  val entityIdentifier: String = "",

  /**
   * Partitioning key for the event. The partitioning key controls the ordering and sharding
   * of events on a topic. Events on a topic with the same partitioning key are delivered on
   * the same shard and in the order in which they were published.  Typically, the entity
   * identifier is also used as a partitioning key, such that all of events on a topic for
   * a given entity get delivered in the order in which they were submitted. However,
   * producing applications may include an alternate partition key as part of the event
   * to support ordering/sharding at a level above the individual entity. For example,
   * a card processing system may want to shard and order all credit card changes relative
   * to the customer to whom the card belongs; in this case the entity identifier is the credit
   * card modified by the event, but the partition key is the token of the customer owning the card.
   */
  val partitionKey: ByteString = if (entityIdentifier.isBlank()) id else entityIdentifier.encodeUtf8(),

  /**
   * Additional context information about the event, added and examined by infrastructure elements
   */
  val headers: Map<String, ByteString> = mapOf()
) {
  fun <A : Message<*, *>> bodyAs(adapter: ProtoAdapter<A>): A = adapter.decode(body)

  inline fun <reified A : Message<*, *>> bodyAs(): A = bodyAs(
      ProtoAdapter.get(A::class.java))

  fun <A : Message<*, *>> header(name: String, adapter: ProtoAdapter<A>): A? =
      headers[name]?.let { adapter.decode(it) }

  inline fun <reified A : Message<*, *>> header(name: String) =
      header(name, ProtoAdapter.get(A::class.java))
}
