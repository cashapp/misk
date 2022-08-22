package misk.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class FakeClock(
  epochMillis: Long = Instant.parse("2018-01-01T00:00:00Z").toEpochMilli(),
  private val zone: ZoneId = ZoneId.of("UTC")
) : Clock() {

  private val millis: AtomicLong = AtomicLong(epochMillis)

  override fun getZone(): ZoneId = zone

  override fun withZone(zone: ZoneId): Clock = FakeClock(millis.get(), zone)

  override fun instant(): Instant = Instant.ofEpochMilli(millis.get()).atZone(zone).toInstant()

  fun add(d: Duration) = millis.addAndGet(d.toMillis())

  /**
   * Note that unlike adding a [Duration] the exact amount that is added to the clock will depend on
   * its current value and timezone. Not all days, months or years have the same length. See the
   * documentation for [Period].
   */
  fun add(p: Period) = millis.getAndUpdate { millis ->
    Instant.ofEpochMilli(millis).atZone(zone).plus(p).toInstant().toEpochMilli()
  }

  fun add(n: Long, unit: TimeUnit) = millis.addAndGet(TimeUnit.MILLISECONDS.convert(n, unit))

  fun setNow(instant: Instant) = millis.set(instant.toEpochMilli())
}
