package misk.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runInterruptible

internal class ResponseSinkChannel<T : Any>(private val channel: Channel<T>, private val sink: ResponseSink<T>) :
  SendChannel<T> by channel {

  // A servlet write may block on a slow reader. Keep the action's runBlocking event loop free to process
  // deadlines and cancellation, which interrupts the write rather than waiting for the connector idle timeout.
  suspend fun bridgeToSink() = channel.consumeEach { runInterruptible(Dispatchers.IO) { sink.write(it) } }
}
