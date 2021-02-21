package misk.eventrouter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

sealed class SocketEvent {
  data class Event(val topic: String, val message: String) : SocketEvent()
  data class Subscribe(val topic: String) : SocketEvent()
  data class Unsubscribe(val topic: String) : SocketEvent()
  data class Ack(val topic: String) : SocketEvent()
  object UnknownItem : SocketEvent()
}

object SocketEventJsonAdapter {
  data class SocketEventJson(
    val type: String,
    val topic: String?,
    val message: String?
  )

  @FromJson fun fromJson(json: SocketEventJson): SocketEvent {
    return when (json.type) {
      "event" -> SocketEvent.Event(json.topic!!, json.message!!)
      "subscribe" -> SocketEvent.Subscribe(json.topic!!)
      "unsubscribe" -> SocketEvent.Unsubscribe(json.topic!!)
      "ack" -> SocketEvent.Ack(json.topic!!)
      else -> SocketEvent.UnknownItem
    }
  }

  @ToJson fun toJson(item: SocketEvent): SocketEventJson {
    return when (item) {
      is SocketEvent.Event -> SocketEventJson(
        "event", item.topic,
        item.message
      )
      is SocketEvent.Subscribe -> SocketEventJson(
        "subscribe", item.topic,
        null
      )
      is SocketEvent.Unsubscribe -> SocketEventJson(
        "unsubscribe",
        item.topic, null
      )
      is SocketEvent.Ack -> SocketEventJson(
        "ack", item.topic, null
      )
      else -> SocketEventJson("unknown", null, null)
    }
  }
}
