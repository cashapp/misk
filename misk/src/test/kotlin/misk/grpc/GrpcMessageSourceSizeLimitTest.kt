package misk.grpc

import com.squareup.protos.test.grpc.HelloRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.Test

class GrpcMessageSourceSizeLimitTest {
  /** A message within the limit still decodes normally. */
  @Test
  fun messageWithinLimitDecodes() {
    val buffer = Buffer()
    buffer.write("000000000b0a096c6f63616c686f7374".decodeHex())
    val reader = GrpcMessageSource(buffer, HelloRequest.ADAPTER, maxMessageBytes = 11)

    assertEquals(HelloRequest("localhost"), reader.read())
  }

  /** An oversized on-the-wire frame is rejected before it is buffered. */
  @Test
  fun oversizedFrameIsRejected() {
    val buffer = Buffer()
    buffer.writeByte(0) // identity
    buffer.writeInt(0xff) // declared message length, larger than the limit below
    val reader = GrpcMessageSource(buffer, HelloRequest.ADAPTER, maxMessageBytes = 8)

    assertFailsWith<GrpcMessageTooLargeException> { reader.read() }
  }

  /**
   * A small gzip frame that inflates past the limit is rejected while decompressing, rather than buffering the whole
   * inflated message into heap.
   */
  @Test
  fun decompressionBombIsRejected() {
    val encoded = HelloRequest.ADAPTER.encode(HelloRequest("a".repeat(10_000)))

    val gzipped = Buffer()
    GzipSink(gzipped).buffer().use { it.write(encoded) }
    val compressed = gzipped.readByteString()
    // The compressed frame is tiny; only the decoded message exceeds the limit.
    assert(compressed.size < 100)

    val frame = Buffer()
    frame.writeByte(1) // gzip
    frame.writeInt(compressed.size)
    frame.write(compressed)
    val reader = GrpcMessageSource(frame, HelloRequest.ADAPTER, "gzip", maxMessageBytes = 100)

    assertFailsWith<GrpcMessageTooLargeException> { reader.read() }
  }
}
