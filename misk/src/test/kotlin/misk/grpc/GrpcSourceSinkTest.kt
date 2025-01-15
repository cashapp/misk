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
    val writer = GrpcMessageSink(buffer, HelloRequest.ADAPTER, "identity")
    writer.write(HelloRequest("localhost"))
    writer.close()

    assertEquals("000000000b0a096c6f63616c686f7374".decodeHex(), buffer.readByteString())
  }

  @Test
  fun grpcMessageSinkHelloReply() {
    val buffer = Buffer()
    val writer = GrpcMessageSink(buffer, HelloReply.ADAPTER, "identity")
    writer.write(HelloReply("Hello localhost"))
    writer.close()

    assertEquals("00000000110a0f48656c6c6f206c6f63616c686f7374".decodeHex(), buffer.readByteString())
  }
}
