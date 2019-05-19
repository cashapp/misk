package misk.grpc

import java.io.Closeable
import java.util.concurrent.LinkedBlockingDeque

interface GrpcSendChannel<T> : Closeable {
  fun send(message: T)
}

interface GrpcReceiveChannel<T> {
  fun receiveOrNull(): T?
}

fun <T> GrpcReceiveChannel<T>.consumeEach(block: (T) -> Unit) {
  while (true) {
    val message = receiveOrNull() ?: return
    block(message)
  }
}

class BlockingGrpcChannel<T> : GrpcSendChannel<T>, GrpcReceiveChannel<T> {
  private val queue = LinkedBlockingDeque<T>(1)
  private object Eof

  override fun send(message: T) {
    queue.put(message)
  }

  @Suppress("UNCHECKED_CAST")
  override fun receiveOrNull(): T? {
    val message = queue.take()
    if (message == Eof) {
      queue.put(Eof as T)
      return null
    }
    return message
  }

  @Suppress("UNCHECKED_CAST")
  override fun close() {
    queue.put(Eof as T)
  }
}