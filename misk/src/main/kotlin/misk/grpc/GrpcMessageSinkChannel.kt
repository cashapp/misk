package misk.grpc

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runInterruptible

/**
 * Bridges a [GrpcMessageSink] to a [Channel].
 *
 * This is the primary mechanism for suspending gRPC calls to handle
 * Server response streaming. The [GrpcMessageSinkChannel] can be passed
 * to the gRPC Action function as a SendChannel to write responses to.
 *
 */
internal class GrpcMessageSinkChannel<T : Any>(
  private val channel: Channel<T>,
  private val sink: GrpcMessageSink<T>,
) : SendChannel<T> by channel {

  /**
   * Bridges the channel to the sink.
   *
   * This will read from the [channel] and write the messages to the [sink]
   * until the channel is closed for sending.
   */
  suspend fun bridgeToSink() =
    channel.consumeEach {
      runInterruptible {
        sink.write(it)
      }
    }
}
