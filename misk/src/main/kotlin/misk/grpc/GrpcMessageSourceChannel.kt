package misk.grpc

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runInterruptible

/**
 * Bridges a [GrpcMessageSource] to a [Channel].
 *
 * This is the primary mechanism for suspending gRPC calls to handle Client request streaming. The
 * [GrpcMessageSourceChannel] can be passed to the gRPC Action function as a ReceiveChannel to read requests from.
 */
internal class GrpcMessageSourceChannel<T : Any>(
  private val channel: Channel<T>,
  private val source: GrpcMessageSource<T>,
  private val coroutineContext: CoroutineContext,
) : ReceiveChannel<T> by channel {

  /**
   * Bridges the source to the channel.
   *
   * This will read from the [source] and send the messages to the [channel] until the [source] is exhausted.
   */
  suspend fun bridgeFromSource() {
    try {
      while (true) {
        val request = runInterruptible(coroutineContext) { source.read() } ?: break
        channel.send(request)
      }
    } finally {
      channel.close()
    }
  }
}
