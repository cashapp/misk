package misk.web

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runInterruptible

internal class ResponseSinkChannel<T : Any>(private val channel: Channel<T>, private val sink: ResponseSink<T>) :
  SendChannel<T> by channel {

  suspend fun bridgeToSink() = channel.consumeEach { runInterruptible { sink.write(it) } }
}
