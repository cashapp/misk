package com.squareup.chat

import misk.web.ConnectWebSocket
import misk.web.PathParam
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Singleton

/**
 * ChatWebSocketAction is a basic chat program which supports multiple chat rooms and their
 * histories through web socket connections.
 */
@Singleton
class ChatWebSocketAction : WebAction {
  private val rooms = mutableMapOf<String, Room>()

  @ConnectWebSocket("/room/{name}")
  fun chat(@PathParam name: String, webSocket: WebSocket): WebSocketListener {
    val room = rooms.getOrDefault(name, Room(name))
    room.join(webSocket)
    rooms[name] = room
    return room
  }
}

private class Room(private val name: String) : WebSocketListener() {
  private val history = CopyOnWriteArrayList<String>()
  private val sockets = CopyOnWriteArrayList<WebSocket>()

  fun join(webSocket: WebSocket) {
    webSocket.send("Welcome to $name!")

    history.forEach { webSocket.send(it) }
    sockets.add(webSocket)
  }

  override fun onMessage(webSocket: WebSocket, text: String) {
    history.add(text)
    sockets.forEach { it.send(text) }
  }

  override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
    sockets.remove(webSocket)
  }

  override fun onFailure(webSocket: WebSocket, t: Throwable) {
    sockets.remove(webSocket)
  }
}
