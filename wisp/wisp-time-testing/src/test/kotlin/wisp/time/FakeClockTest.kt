package wisp.time

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

internal class FakeClockTest {

  @Test
  fun defaultTimestamp() {
    val clock = FakeClock()
    val utc = ZoneId.of("UTC")
    val zonedDateTime = clock.instant().atZone(utc)
    assertThat(zonedDateTime).isEqualTo(ZonedDateTime.of(2018, 1, 1, 0, 0, 0, 0, utc))
  }

  @Test
  fun adjustTime() {
    val clock = FakeClock(epochMillis = 0L)
    assertThat(clock.millis()).isEqualTo(0L)

    assertThat(clock.add(Duration.ofSeconds(20))).isEqualTo(20000L)
    assertThat(clock.millis()).isEqualTo(20000L)

    assertThat(clock.add(45, TimeUnit.HOURS)).isEqualTo(162020000L)
    assertThat(clock.millis()).isEqualTo(162020000L)
  }

  @Test
  fun differentTimeZones() {
    val clock = FakeClock(epochMillis = 0L, zone = ZoneId.of("America/Los_Angeles"))
    assertThat(clock.zone.id).isEqualTo("America/Los_Angeles")

    clock.add(45, TimeUnit.HOURS)

    val newClock = clock.withZone(ZoneId.of("America/New_York"))
    assertThat(newClock.zone.id).isEqualTo("America/New_York")
    assertThat(newClock.instant()).isEqualTo(Instant.ofEpochMilli(162000000L))
  }

  @Test
  fun addPeriod() {
    val clock = FakeClock(epochMillis = 0L, zone = ZoneId.of("America/Los_Angeles"))

    // Just prior to winding clock forward
    val startInstant = Instant.parse("1970-04-26T00:00:00Z")
    clock.setNow(startInstant)

    // This day is only 23 hours long because of the DST transition
    assertThat(clock.add(Period.ofDays(1))).isEqualTo(startInstant.plus(23L, ChronoUnit.HOURS).toEpochMilli())
    assertThat(clock.instant()).isEqualTo(startInstant.plus(23L, ChronoUnit.HOURS))

    assertThat(clock.add(Period.ofMonths(2))).isEqualTo(Instant.parse("1970-06-26T23:00:00Z").toEpochMilli())
    assertThat(clock.instant()).isEqualTo(Instant.parse("1970-06-26T23:00:00Z"))
  }
}
