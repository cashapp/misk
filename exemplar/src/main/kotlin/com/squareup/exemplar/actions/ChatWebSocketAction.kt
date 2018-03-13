package com.squareup.exemplar.actions

import misk.web.ConnectWebSocket
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import misk.web.mediatype.MediaTypes
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Singleton

/**
 * ChatWebSocketAction is a basic chat program which supports multiple chat
 * rooms and their histories through web socket connections.
 */
@Singleton
class ChatWebSocketAction : WebAction {
  private val rooms = mutableMapOf<String, Room>()

  @ConnectWebSocket("/room/{name}")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun chat(@PathParam name: String, webSocket: WebSocket<Message>): WebSocketListener<Message> {
    val room = rooms.getOrDefault(name, Room(name))
    room.join(webSocket)
    rooms[name] = room
    return room
  }
}

data class Message(
  val user: String,
  val message: String
)

private class Room(private val name: String) : WebSocketListener<Message>() {
  private val history = CopyOnWriteArrayList<Message>()
  private val sockets = CopyOnWriteArrayList<WebSocket<Message>>()

  fun join(webSocket: WebSocket<Message>) {
    webSocket.send(Message("admin", "Welcome to $name!"))

    history.forEach { webSocket.send(it) }
    sockets.add(webSocket)
  }

  override fun onMessage(webSocket: WebSocket<Message>, content: Message) {
    history.add(content)
    sockets.forEach { it.send(content) }
  }

  override fun onClosing(webSocket: WebSocket<Message>, code: Int, reason: String?) {
    sockets.remove(webSocket)
  }

  override fun onFailure(webSocket: WebSocket<Message>, t: Throwable) {
    sockets.remove(webSocket)
  }
}
