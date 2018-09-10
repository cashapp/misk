package misk.grpc

import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GrpcReaderWriterTest {
  @Test
  fun grpcReaderHelloRequest() {
    val buffer = Buffer()
    buffer.write("000000000b0a096c6f63616c686f7374".decodeHex())
    val reader = GrpcReader.get(buffer, HelloRequest.ADAPTER)
    assertThat(reader.readMessage()).isEqualTo(HelloRequest("localhost"))
  }

  @Test
  fun grpcReaderHelloReply() {
    val buffer = Buffer()
    buffer.write("00000000110a0f48656c6c6f206c6f63616c686f7374".decodeHex())
    val reader = GrpcReader.get(buffer, HelloReply.ADAPTER)
    assertThat(reader.readMessage()).isEqualTo(HelloReply("Hello localhost"))
  }

  @Test
  fun grpcWriterHelloRequest() {
    val buffer = Buffer()
    val writer = GrpcWriter.get(buffer, HelloRequest.ADAPTER)
    writer.writeMessage(HelloRequest("localhost"))
    writer.flush()
    writer.close()
    assertThat(buffer.readByteString())
        .isEqualTo("000000000b0a096c6f63616c686f7374".decodeHex())
  }

  @Test
  fun grpcWriterHelloReply() {
    val buffer = Buffer()
    val writer = GrpcWriter.get(buffer, HelloReply.ADAPTER)
    writer.writeMessage(HelloReply("Hello localhost"))
    writer.flush()
    writer.close()
    assertThat(buffer.readByteString()).isEqualTo(
        "00000000110a0f48656c6c6f206c6f63616c686f7374".decodeHex())
  }
}