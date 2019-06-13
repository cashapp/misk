package misk.grpc

import java.io.Closeable

interface GrpcSendChannel<T> : Closeable {
  fun send(message: T)
}

interface GrpcReceiveChannel<T> : Closeable {
  fun receiveOrNull(): T?
}

fun <T> GrpcReceiveChannel<T>.consumeEachAndClose(block: (T) -> Unit) {
  use {
    while (true) {
      val message = receiveOrNull() ?: return
      block(message)
    }
  }
}
