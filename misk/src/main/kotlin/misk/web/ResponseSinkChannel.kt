package misk.web

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach

internal class ResponseSinkChannel<T : Any>(
  private val channel: Channel<T>,
  private val sink: ResponseSink<T>,
  private val coroutineContext: CoroutineContext,
) : SendChannel<T> by channel {

  /**
   * Bridges the channel to the sink, running each write on [coroutineContext] so a slow reader doesn't block the
   * action's event loop. See [StreamingResponseWriteDispatcher].
   */
  suspend fun bridgeToSink() = channel.consumeEach { runInterruptibleWrite(coroutineContext) { sink.write(it) } }
}
