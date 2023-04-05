package com.squareup.chat.actions

import misk.redis.Redis
import misk.web.ConnectWebSocket
import misk.web.PathParam
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import redis.clients.jedis.JedisPubSub
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * ChatWebSocketAction is a basic chat program which supports multiple chat rooms and their
 * histories through web socket connections.
 */
@Singleton
class ChatWebSocketAction @Inject constructor(
  private val redis: Redis
) : WebAction {
  @ConnectWebSocket("/room/{name}")
  fun chat(@PathParam name: String, webSocket: WebSocket): WebSocketListener {
    webSocket.send("Welcome to $name!")
    val pubSub = object : JedisPubSub() {
      override fun onSubscribe(channel: String, subscribedChannels: Int) {
        webSocket.send("Subscribed to $name")
      }

      override fun onMessage(channel: String, message: String) {
        webSocket.send(message)
      }
    }
    thread { redis.subscribe(pubSub, name) }

    return object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        redis.publish(name, text)
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String?) {
        pubSub.unsubscribe()
      }

      override fun toString(): String {
        return "$name.webSocketListener"
      }
    }
  }
}
