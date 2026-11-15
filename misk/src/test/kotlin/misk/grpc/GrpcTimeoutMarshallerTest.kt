package misk.grpc

import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GrpcTimeoutMarshallerTest {
  @Test
  fun `toAsciiString formats nanoseconds correctly`() {
    // Test nanoseconds (n)
    assertThat(GrpcTimeoutMarshaller.toAsciiString(50)).isEqualTo("50n")
    assertThat(GrpcTimeoutMarshaller.toAsciiString(99999999)).isEqualTo("99999999n")

    // Test microseconds (u)
    assertThat(GrpcTimeoutMarshaller.toAsciiString(100_000_000)).isEqualTo("100000u")
    assertThat(GrpcTimeoutMarshaller.toAsciiString(99_999_999_999)).isEqualTo("99999999u")

    // Test milliseconds (m)
    assertThat(GrpcTimeoutMarshaller.toAsciiString(100_000_000_000)).isEqualTo("100000m")
    assertThat(GrpcTimeoutMarshaller.toAsciiString(99_999_999_999_999)).isEqualTo("99999999m")

    // Test seconds (S)
    assertThat(GrpcTimeoutMarshaller.toAsciiString(100_000_000_000_000)).isEqualTo("100000S")
    assertThat(GrpcTimeoutMarshaller.toAsciiString(99_999_999_999_999_999)).isEqualTo("99999999S")
    assertThat(GrpcTimeoutMarshaller.toAsciiString(TimeUnit.SECONDS.toNanos(2))).isEqualTo("2000000u")

    // Test minutes (M)
    assertThat(GrpcTimeoutMarshaller.toAsciiString(100_000_000_000_000_000)).isEqualTo("1666666M")
    assertThat(GrpcTimeoutMarshaller.toAsciiString(TimeUnit.MINUTES.toNanos(5))).isEqualTo("300000m")

    // Test hours (H)
    assertThat(GrpcTimeoutMarshaller.toAsciiString(TimeUnit.HOURS.toNanos(1))).isEqualTo("3600000m")
    assertThat(GrpcTimeoutMarshaller.toAsciiString(TimeUnit.HOURS.toNanos(999))).isEqualTo("3596400S")
  }

  @Test
  fun `toAsciiString validates input`() {
    assertThrows<IllegalArgumentException> { GrpcTimeoutMarshaller.toAsciiString(-1) }
  }

  @Test
  fun `parseAsciiString parses all units correctly`() {
    // Test nanoseconds (n)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("50n")).isEqualTo(50)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("99999999n")).isEqualTo(99999999)

    // Test microseconds (u)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("1u")).isEqualTo(TimeUnit.MICROSECONDS.toNanos(1))
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("999999u")).isEqualTo(TimeUnit.MICROSECONDS.toNanos(999999))

    // Test milliseconds (m)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("1m")).isEqualTo(TimeUnit.MILLISECONDS.toNanos(1))
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("999999m")).isEqualTo(TimeUnit.MILLISECONDS.toNanos(999999))

    // Test seconds (S)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("1S")).isEqualTo(TimeUnit.SECONDS.toNanos(1))
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("99999S")).isEqualTo(TimeUnit.SECONDS.toNanos(99999))

    // Test minutes (M)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("1M")).isEqualTo(TimeUnit.MINUTES.toNanos(1))
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("99999M")).isEqualTo(TimeUnit.MINUTES.toNanos(99999))

    // Test hours (H)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("1H")).isEqualTo(TimeUnit.HOURS.toNanos(1))
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("99999H")).isEqualTo(TimeUnit.HOURS.toNanos(99999))
  }

  @Test
  fun `parseAsciiString validates input`() {
    // Test empty string
    assertThrows<IllegalArgumentException> { GrpcTimeoutMarshaller.parseAsciiString("") }

    // Test too long string
    assertThrows<IllegalArgumentException> { GrpcTimeoutMarshaller.parseAsciiString("1234567890S") }

    // Test invalid unit
    assertThrows<IllegalArgumentException> { GrpcTimeoutMarshaller.parseAsciiString("100X") }

    // Test invalid format (no unit)
    assertThrows<IllegalArgumentException> { GrpcTimeoutMarshaller.parseAsciiString("100") }

    // Test invalid format (non-numeric)
    assertThrows<NumberFormatException> { GrpcTimeoutMarshaller.parseAsciiString("abc123n") }
  }

  @Test
  fun `parseAsciiString can parse negative values`() {
    // Note: While the gRPC spec doesn't allow negative timeouts,
    // the parser can handle them. This documents the behavior for
    // cases where upstream services send invalid negative timeouts.
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("-5m")).isEqualTo(TimeUnit.MILLISECONDS.toNanos(-5))
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("-100n")).isEqualTo(-100)
    assertThat(GrpcTimeoutMarshaller.parseAsciiString("-10S")).isEqualTo(TimeUnit.SECONDS.toNanos(-10))
  }

  @Test
  fun `roundtrip conversion works correctly`() {
    val testValues =
      listOf(
        50L, // nanoseconds
        TimeUnit.MICROSECONDS.toNanos(100), // microseconds
        TimeUnit.MILLISECONDS.toNanos(200), // milliseconds
        TimeUnit.SECONDS.toNanos(300), // seconds
        TimeUnit.MINUTES.toNanos(400), // minutes
        TimeUnit.HOURS.toNanos(500), // hours
      )

    for (value in testValues) {
      val encoded = GrpcTimeoutMarshaller.toAsciiString(value)
      val decoded = GrpcTimeoutMarshaller.parseAsciiString(encoded)
      assertThat(decoded).isEqualTo(value)
    }
  }
}
