package misk.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import wisp.time.FakeClock as WispFakeClock

class FakeClock(
  epochMillis: Long = Instant.parse("2018-01-01T00:00:00Z").toEpochMilli(),
  zone: ZoneId = ZoneId.of("UTC")
) : Clock() {
  internal val wispFakeClock = WispFakeClock(epochMillis = epochMillis, zone = zone)

  override fun getZone(): ZoneId = wispFakeClock.zone

  override fun withZone(zone: ZoneId): Clock = wispFakeClock.withZone(zone = zone)

  override fun instant(): Instant = wispFakeClock.instant()

  fun add(d: Duration) = wispFakeClock.add(d = d)

  /**
   * Note that unlike adding a [Duration] the exact amount that is added to the clock will depend on
   * its current time and timezone. Not all days, months or years have the same length. See the
   * documentation for [Period].
   */
  fun add(p: Period) = wispFakeClock.add(p = p)

  fun add(n: Long, unit: TimeUnit) = wispFakeClock.add(n = n, unit = unit)

  fun setNow(instant: Instant) = wispFakeClock.setNow(instant = instant)
}
