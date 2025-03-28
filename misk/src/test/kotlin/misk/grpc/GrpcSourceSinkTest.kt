package misk.grpc

import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GrpcSourceSinkTest {
  @Test
  fun grpcMessageSourceHelloRequest() {
    val buffer = Buffer()
    buffer.write("000000000b0a096c6f63616c686f7374".decodeHex())
    val reader = GrpcMessageSource(buffer, HelloRequest.ADAPTER)

    assertEquals(HelloRequest("localhost"), reader.read())
  }

  @Test
  fun grpcMessageSourceHelloReply() {
    val buffer = Buffer()
    buffer.write("00000000110a0f48656c6c6f206c6f63616c686f7374".decodeHex())
    val reader = GrpcMessageSource(buffer, HelloReply.ADAPTER)

    assertEquals(HelloReply("Hello localhost"), reader.read())
  }

  @Test
  fun grpcMessageSinkHelloRequest() {
    val buffer = Buffer()
    val writer = GrpcMessageSink(
      sink = buffer,
      minMessageToCompress = 0,
      messageAdapter = HelloRequest.ADAPTER,
      grpcEncoding = "identity"
    )
    writer.write(HelloRequest("localhost"))
    writer.close()

    assertEquals("000000000b0a096c6f63616c686f7374".decodeHex(), buffer.readByteString())
  }

  @Test
  fun grpcMessageSinkHelloReply() {
    val buffer = Buffer()
    val writer = GrpcMessageSink(
      sink = buffer,
      minMessageToCompress = 0,
      messageAdapter = HelloReply.ADAPTER,
      grpcEncoding = "identity"
    )
    writer.write(HelloReply("Hello localhost"))
    writer.close()

    assertEquals("00000000110a0f48656c6c6f206c6f63616c686f7374".decodeHex(), buffer.readByteString())
  }

  @Test
  fun grpcMessageSourceCompressedHelloRequest() {
    val buffer = Buffer()
    buffer.write(
      "010000001f1f8b0800000000000000e3e2ccc94f4eccc9c82f2e01002fdef60d0b000000".decodeHex()
    )
    val reader = GrpcMessageSource(buffer, HelloRequest.ADAPTER, "gzip")

    assertEquals(HelloRequest("localhost"), reader.read())
  }

  @Test
  fun messageLargerThanMinimumSizeIsCompressed() {
    val message = HelloRequest("localhost")
    val encodedMessage = HelloRequest.ADAPTER.encode(message)
    assertEquals(encodedMessage.size, 11)

    val buffer = Buffer()
    val writer = GrpcMessageSink(
      sink = buffer,
      minMessageToCompress = 10,
      messageAdapter = HelloRequest.ADAPTER,
      grpcEncoding = "gzip"
    )

    writer.write(message)
    writer.close()

    assertEquals(
      "010000001f1f8b0800000000000000e3e2ccc94f4eccc9c82f2e01002fdef60d0b000000".decodeHex(),
      buffer.readByteString()
    )
  }

  @Test
  fun messageEqualToMinimumSizeIsCompressed() {
    val message = HelloRequest("localhost")
    val encodedMessage = HelloRequest.ADAPTER.encode(message)
    assertEquals(encodedMessage.size, 11)

    val buffer = Buffer()
    val writer = GrpcMessageSink(
      sink = buffer,
      minMessageToCompress = 11,
      messageAdapter = HelloRequest.ADAPTER,
      grpcEncoding = "gzip"
    )

    writer.write(message)
    writer.close()

    assertEquals(
      "010000001f1f8b0800000000000000e3e2ccc94f4eccc9c82f2e01002fdef60d0b000000".decodeHex(),
      buffer.readByteString()
    )
  }

  @Test
  fun messageSmallerThanMinimumSizeIsNotCompressed() {
    val message = HelloRequest("localhost")
    val encodedMessage = HelloRequest.ADAPTER.encode(message)
    assertEquals(encodedMessage.size, 11)

    val buffer = Buffer()
    val writer = GrpcMessageSink(
      sink = buffer,
      minMessageToCompress = 12,
      messageAdapter = HelloRequest.ADAPTER,
      grpcEncoding = "gzip"
    )

    writer.write(message)
    writer.close()

    assertEquals(
      "000000000b0a096c6f63616c686f7374".decodeHex(),
      buffer.readByteString()
    )
  }
}
