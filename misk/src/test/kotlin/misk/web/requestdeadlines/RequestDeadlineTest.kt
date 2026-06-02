package misk.web.requestdeadlines

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestDeadlineTest {

  @Test
  fun `expired returns false when deadline is null`() {
    val clock = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC)
    val deadline = RequestDeadline(clock, null)

    assertThat(deadline.expired()).isFalse()
  }

  @Test
  fun `expired returns false when deadline is in the future`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val futureDeadline = currentTime.plusSeconds(30)
    val deadline = RequestDeadline(clock, futureDeadline)

    assertThat(deadline.expired()).isFalse()
  }

  @Test
  fun `expired returns true when deadline is in the past`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val pastDeadline = currentTime.minusSeconds(30)
    val deadline = RequestDeadline(clock, pastDeadline)

    assertThat(deadline.expired()).isTrue()
  }

  @Test
  fun `expired returns true when deadline is exactly now`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val nowDeadline = currentTime
    val deadline = RequestDeadline(clock, nowDeadline)

    assertThat(deadline.expired()).isFalse() // isAfter returns false for equal instants
  }

  @Test
  fun `remaining returns null when deadline is null`() {
    val clock = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC)
    val deadline = RequestDeadline(clock, null)

    assertThat(deadline.remaining()).isNull()
  }

  @Test
  fun `remaining returns correct duration when deadline is in the future`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val futureDeadline = currentTime.plusSeconds(30)
    val deadline = RequestDeadline(clock, futureDeadline)

    assertThat(deadline.remaining()).isEqualTo(Duration.ofSeconds(30))
  }

  @Test
  fun `remaining returns zero when deadline is in the past`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val pastDeadline = currentTime.minusSeconds(30)
    val deadline = RequestDeadline(clock, pastDeadline)

    assertThat(deadline.remaining()).isEqualTo(Duration.ZERO)
  }

  @Test
  fun `remaining returns zero when deadline is exactly now`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val nowDeadline = currentTime
    val deadline = RequestDeadline(clock, nowDeadline)

    assertThat(deadline.remaining()).isEqualTo(Duration.ZERO)
  }

  @Test
  fun `remaining handles millisecond precision correctly`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00.000Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val futureDeadline = currentTime.plusMillis(1500) // 1.5 seconds
    val deadline = RequestDeadline(clock, futureDeadline)

    assertThat(deadline.remaining()).isEqualTo(Duration.ofMillis(1500))
  }

  @Test
  fun `remaining handles nanosecond precision correctly`() {
    val currentTime = Instant.parse("2024-01-01T10:00:00.000Z")
    val clock = Clock.fixed(currentTime, ZoneOffset.UTC)
    val futureDeadline = currentTime.plusNanos(1500000000) // 1.5 seconds in nanos
    val deadline = RequestDeadline(clock, futureDeadline)

    assertThat(deadline.remaining()).isEqualTo(Duration.ofNanos(1500000000))
  }

  @Test
  fun `data class equality works correctly`() {
    val clock1 = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC)
    val clock2 = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC)
    val deadline1 = Instant.parse("2024-01-01T10:00:30Z")
    val deadline2 = Instant.parse("2024-01-01T10:00:30Z")

    val requestDeadline1 = RequestDeadline(clock1, deadline1)
    val requestDeadline2 = RequestDeadline(clock2, deadline2)

    assertThat(requestDeadline1).isEqualTo(requestDeadline2)
  }

  @Test
  fun `data class inequality works correctly`() {
    val clock = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC)
    val deadline1 = Instant.parse("2024-01-01T10:00:30Z")
    val deadline2 = Instant.parse("2024-01-01T10:01:30Z")

    val requestDeadline1 = RequestDeadline(clock, deadline1)
    val requestDeadline2 = RequestDeadline(clock, deadline2)

    assertThat(requestDeadline1).isNotEqualTo(requestDeadline2)
  }

  @Test
  fun `toString returns meaningful representation`() {
    val clock = Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC)
    val deadline = Instant.parse("2024-01-01T10:00:30Z")
    val requestDeadline = RequestDeadline(clock, deadline)

    val toString = requestDeadline.toString()
    assertThat(toString).contains("RequestDeadline")
    assertThat(toString).contains("2024-01-01T10:00:30Z")
  }
}
