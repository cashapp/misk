package misk.grpc

import com.squareup.protos.test.grpc.HelloRequest
import io.kotest.assertions.nondeterministic.eventually
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GrpcMessageSinkChannelTest {

  private val buffer = Buffer()
  private val writer = GrpcMessageSink(buffer, HelloRequest.ADAPTER, "identity")


  @AfterEach
  fun tearDown() {
    writer.close()
    buffer.close()
  }

  @Test
  fun `test bridge from Channel to GrpcMessageSink HelloRequest`() = runTest {

    val channel = Channel<HelloRequest>()
    launch { GrpcMessageSinkChannel(channel, writer).bridgeToSink() }

    channel.send(HelloRequest("localhost"))
    eventually(100.milliseconds) {
      buffer.readByteString().also { byteString ->
        assertThat(byteString)
          .isEqualTo("000000000b0a096c6f63616c686f7374".decodeHex())
      }
    }

    channel.send(HelloRequest("proxy"))
    eventually(100.milliseconds) {
      buffer.readByteString().also { byteString ->
        assertThat(byteString)
          .isEqualTo("00000000070a0570726f7879".decodeHex())
      }
    }
    channel.close()

  }

}

