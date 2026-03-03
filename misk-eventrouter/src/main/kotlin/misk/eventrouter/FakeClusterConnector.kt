package misk.eventrouter

import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FakeClusterConnector @Inject constructor() : ClusterConnector {
  val queue = LinkedBlockingQueue<Action>()
  private var nextHostId = 1
  private val peers = mutableMapOf<String, TopicPeer>()

  /** Returns the number of actions that were executed. */
  fun processEverything(): Int {
    var result = 0
    while (true) {
      val action = queue.poll() ?: return result
      result++

      when (action) {
        is Action.JoinCluster -> {
          peers[action.hostname] = action.topicPeer
          peersChanged()
        }

        is Action.LeaveCluster -> {
          val hostname = hostnameOfPeer(action.topicPeer)
          peers.remove(hostname)
          peersChanged()
        }

        is Action.ConnectSocket -> {
          // Connect the two web sockets together.
          val serverWebSocket = FakeWebSocket(action.clientWebSocket.serverHostname, true)
          action.clientWebSocket.twin = serverWebSocket
          serverWebSocket.twin = action.clientWebSocket

          // Get the server to receive the incoming web socket
          val peer = peers[action.clientWebSocket.serverHostname]!!
          // TODO: notify the websocket of a connection failure if there's no such peer.
          val listener = peer.acceptWebSocket(serverWebSocket)
          serverWebSocket.listener = listener
        }

        is Action.WebSocketSend -> {
          action.fakeWebSocket.twin!!.listener.onMessage(action.fakeWebSocket.twin!!, action.text)
        }

        is Action.WebSocketClose -> {
          action.fakeWebSocket.closeImmediately(action.code, action.reason)
        }

        is Action.WebSocketCancel -> {
          action.fakeWebSocket.cancelImmediately(IOException("cancel"))
        }
      }
    }
  }

  private fun hostnameOfPeer(peer: TopicPeer): String {
    for ((key, value) in peers) {
      if (value === peer) {
        return key
      }
    }
    throw IllegalArgumentException("unexpected peer: $peer")
  }

  private fun peersChanged() {
    val hosts = peers.keys.toList()

    for ((hostname, peer) in peers) {
      peer.clusterChanged(ClusterSnapshot(
          hosts,
          hostname
      ))
    }
  }

  override fun joinCluster(topicPeer: TopicPeer) {
    val hostname = "host_$nextHostId"
    nextHostId += 1
    queue.add(Action.JoinCluster(topicPeer, hostname))
  }

  override fun leaveCluster(topicPeer: TopicPeer) {
    queue.add(Action.LeaveCluster(topicPeer))
  }

  override fun connectSocket(hostname: String, listener: WebSocketListener): WebSocket {
    val result = FakeWebSocket(hostname, false)
    result.listener = listener
    queue.add(Action.ConnectSocket(result))
    return result
  }

  inner class FakeWebSocket(
    val serverHostname: String,
    val server: Boolean
  ) : WebSocket {
    lateinit var listener: WebSocketListener
    var twin: FakeWebSocket? = null

    override fun queueSize(): Long = TODO()
    override fun send(bytes: ByteString): Boolean = TODO()

    override fun send(text: String): Boolean {
      queue.add(Action.WebSocketSend(this, text))
      return true
    }

    override fun close(code: Int, reason: String?): Boolean {
      queue.add(Action.WebSocketClose(this, code, reason))
      return true
    }

    override fun cancel() {
      queue.add(Action.WebSocketCancel(this))
    }

    fun closeImmediately(code: Int, reason: String?) {
      // TODO(jwilson): figure out if we need to hold onClosing() until no more messages are queued
      twin!!.listener.onClosing(twin!!, code, reason)
      listener.onClosing(this, code, reason)
      twin!!.listener.onClosed(twin!!, code, reason)
      listener.onClosed(this, code, reason)
    }

    fun cancelImmediately(reason: Throwable) {
      twin!!.listener.onFailure(twin!!, reason)
      listener.onFailure(this, reason)
    }
  }

  sealed class Action {
    data class JoinCluster(
      val topicPeer: TopicPeer,
      val hostname: String
    ) : Action()

    data class LeaveCluster(
      val topicPeer: TopicPeer
    ) : Action()

    data class ConnectSocket(
      val clientWebSocket: FakeWebSocket
    ) : Action()

    data class WebSocketSend(
      val fakeWebSocket: FakeWebSocket,
      val text: String
    ) : Action()

    data class WebSocketClose(
      val fakeWebSocket: FakeWebSocket,
      val code: Int,
      val reason: String?
    ) : Action()

    data class WebSocketCancel(
      val fakeWebSocket: FakeWebSocket
    ) : Action()
  }
}
