package misk.grpc

import com.squareup.protos.test.grpc.HelloRequest
import io.kotest.assertions.nondeterministic.eventually
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class GrpcMessageSourceChannelTest {
  private val buffer = Buffer()
  private val reader = GrpcMessageSource(buffer, HelloRequest.ADAPTER)

  @AfterEach
  fun tearDown() {
    reader.close()
    buffer.close()
  }

  @Test
  fun `test bridge from Channel to GrpcMessageSource HelloRequest`() = runTest {
    val buffer = Buffer()
    val reader = GrpcMessageSource(buffer, HelloRequest.ADAPTER)
    val channel = Channel<HelloRequest>()

    buffer.write("000000000b0a096c6f63616c686f7374".decodeHex())
    buffer.write("00000000070a0570726f7879".decodeHex())

    launch { GrpcMessageSourceChannel(channel, reader, coroutineContext).bridgeFromSource() }

    eventually(100.milliseconds) {
      assertEquals(HelloRequest("localhost"), channel.receive())
    }

    eventually(100.milliseconds) {
      assertEquals(HelloRequest("proxy"), channel.receive())
    }
  }
}
