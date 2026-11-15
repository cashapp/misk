package misk.redis.lettuce

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Forwards connections on [listenPort] to [targetPort] on localhost, so tests can make Redis unreachable and reachable
 * again without restarting it.
 */
internal class TcpProxy(val listenPort: Int, private val targetPort: Int) : Closeable {
  private val sockets = CopyOnWriteArrayList<Closeable>()

  fun start() = apply {
    val server = ServerSocket(listenPort, 50, InetAddress.getLoopbackAddress())
    sockets += server
    thread(isDaemon = true, name = "tcp-proxy-$listenPort") {
      while (!server.isClosed) {
        val client = runCatching { server.accept() }.getOrNull() ?: break
        val target = Socket(InetAddress.getLoopbackAddress(), targetPort)
        sockets += client
        sockets += target
        pipe(client, target)
        pipe(target, client)
      }
    }
  }

  override fun close() {
    sockets.forEach { runCatching { it.close() } }
    sockets.clear()
  }

  private fun pipe(from: Socket, to: Socket) =
    thread(isDaemon = true) {
      runCatching { from.getInputStream().copyTo(to.getOutputStream()) }
      runCatching { from.close() }
      runCatching { to.close() }
    }

  companion object {
    fun freePort(): Int = ServerSocket(0).use { it.localPort }
  }
}
