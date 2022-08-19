package misk.time

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
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

    clock.add(Duration.ofSeconds(20))
    assertThat(clock.millis()).isEqualTo(20000L)

    clock.add(45, TimeUnit.HOURS)
    assertThat(clock.millis()).isEqualTo(162020000L)

    clock.add(Period.ofMonths(2))
    assertThat(clock.instant()).isEqualTo(Instant.parse("1970-03-02T21:00:20Z"))
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
}
