package misk.time

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal class FakeClockTest {

  @Test
  fun adjustTime() {
    val clock = FakeClock()
    assertThat(clock.millis()).isEqualTo(0L)

    clock.add(Duration.ofSeconds(20))
    assertThat(clock.millis()).isEqualTo(20000L)

    clock.add(45, TimeUnit.HOURS)
    assertThat(clock.millis()).isEqualTo(162020000L)
  }

  @Test
  fun differentTimeZones() {
    val clock = FakeClock(zone = ZoneId.of("America/Los_Angeles"))
    assertThat(clock.zone.id).isEqualTo("America/Los_Angeles")

    clock.add(45, TimeUnit.HOURS)

    val newClock = clock.withZone(ZoneId.of("America/New_York"))
    assertThat(newClock.zone.id).isEqualTo("America/New_York")
    assertThat(newClock.instant()).isEqualTo(Instant.ofEpochMilli(162000000L))
  }
}
