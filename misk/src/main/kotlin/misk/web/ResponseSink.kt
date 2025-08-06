package misk.web

import com.squareup.wire.MessageSink
import misk.web.marshal.Marshaller
import okio.BufferedSink
import wisp.logging.getLogger
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ResponseSink<T : Any>(
  private val sink: BufferedSink,
  private val httpCall: HttpCall,
  private val responseBodyMarshaller: Marshaller<T>,
) : MessageSink<T> {

  private var closed = AtomicBoolean(false)

  override fun write(message: T) {
    check(!closed.load()) { "closed" }
    val responseBody = responseBodyMarshaller.responseBody(message, httpCall)
    responseBody.writeTo(sink)
    sink.flush()
  }

  override fun cancel() {
    check(!closed.load()) { "closed" }
    // TODO: Cancel the Jetty request.??
  }

  override fun close() {
    if (closed.exchange(true)) return
    sink.close()
  }

  override fun toString() = "ResponseSink"


  companion object {
    private val logger = getLogger<ResponseSink<Any>>()
  }
}
