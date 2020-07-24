package com.squareup.chat.actions

import misk.eventrouter.EventRouter
import misk.eventrouter.Listener
import misk.eventrouter.Subscription
import misk.web.ConnectWebSocket
import misk.web.ConcurrencyLimitsOptOut
import misk.web.PathParam
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import javax.inject.Inject

/**
 * ChatWebSocketAction is a basic chat program which supports multiple chat rooms and their
 * histories through web socket connections.
 */
class ChatWebSocketAction @Inject constructor() : WebAction {
  @Inject lateinit var eventRouter: EventRouter

  @ConnectWebSocket("/room/{name}")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  fun chat(@PathParam name: String, webSocket: WebSocket): WebSocketListener {
    val topic = eventRouter.getTopic<String>(name)
    webSocket.send("Welcome to $name!")

    val listener = object : Listener<String> {
      override fun onEvent(subscription: Subscription<String>, event: String) {
        webSocket.send(event)
      }

      override fun onOpen(subscription: Subscription<String>) = Unit

      override fun onClose(subscription: Subscription<String>) {
        webSocket.close(1000, "Topic owner has probably changed")
      }

      override fun toString(): String {
        return "$name.eventListener"
      }
    }
    val subscription = topic.subscribe(listener)

    return object : WebSocketListener() {
      override fun onMessage(webSocket: WebSocket, text: String) {
        topic.publish(text)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
        subscription.cancel()
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String?) {
        subscription.cancel()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable) {
        subscription.cancel()
      }

      override fun toString(): String {
        return "$name.webSocketListener"
      }
    }
  }
}
