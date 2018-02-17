package misk.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class FakeClock(
    epochMillis: Long = 0,
    private val zone: ZoneId = ZoneId.of("UTC")
) : Clock() {

  private val millis: AtomicLong = AtomicLong(epochMillis)

  override fun getZone(): ZoneId = zone

  override fun withZone(zone: ZoneId): Clock = FakeClock(millis.get(), zone)

  override fun instant(): Instant = Instant.ofEpochMilli(millis.get()).atZone(zone).toInstant()

  fun add(d: Duration) = millis.addAndGet(d.toMillis())

  fun add(
      n: Long,
      unit: TimeUnit
  ) = millis.addAndGet(TimeUnit.MILLISECONDS.convert(n, unit))
}
