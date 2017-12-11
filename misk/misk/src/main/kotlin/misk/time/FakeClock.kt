package misk.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class FakeClock(private val zone: ZoneId = ZoneId.of("UTC")) : Clock() {
  val millis = AtomicLong()

  override fun getZone(): ZoneId {
    return zone
  }

  override fun withZone(zone: ZoneId?): Clock {
    // TODO(mmihic): Adjust for time zone
    throw UnsupportedOperationException("nope")
  }

  override fun instant(): Instant = Instant.ofEpochMilli(millis.get())

  fun add(d: Duration) = millis.addAndGet(d.toMillis())
  fun add(n: Long, unit: TimeUnit) = millis.addAndGet(TimeUnit.MILLISECONDS.convert(n, unit))
}
