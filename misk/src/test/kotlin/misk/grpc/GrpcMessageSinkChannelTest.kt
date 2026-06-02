package misk.grpc

import com.squareup.protos.test.grpc.HelloRequest
import io.kotest.assertions.nondeterministic.eventually
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class GrpcMessageSinkChannelTest {
  private val buffer = Buffer()
  private val writer =
    GrpcMessageSink(
      sink = buffer,
      minMessageToCompress = 0,
      messageAdapter = HelloRequest.ADAPTER,
      grpcEncoding = "identity",
    )

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
      assertEquals("000000000b0a096c6f63616c686f7374".decodeHex(), buffer.readByteString())
    }

    channel.send(HelloRequest("proxy"))
    eventually(100.milliseconds) { assertEquals("00000000070a0570726f7879".decodeHex(), buffer.readByteString()) }
    channel.close()
  }
}
