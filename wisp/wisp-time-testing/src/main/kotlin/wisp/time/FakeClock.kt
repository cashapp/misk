package wisp.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Controllable clock for testing. */
class FakeClock(
  epochMillis: Long = Instant.parse("2018-01-01T00:00:00Z").toEpochMilli(),
  private val zone: ZoneId = ZoneId.of("UTC")
) : Clock() {
  private val millis: AtomicLong = AtomicLong(epochMillis)

  override fun getZone(): ZoneId = zone

  override fun withZone(zone: ZoneId): Clock = FakeClock(millis.get(), zone)

  override fun instant(): Instant = Instant.ofEpochMilli(millis.get()).atZone(zone).toInstant()

  /** Advance the clock by specified [Duration]. */
  fun add(d: Duration) = millis.addAndGet(d.toMillis())

  /** Advance the clock by specified amount `n` of [TimeUnit]. */
  fun add(n: Long, unit: TimeUnit) = millis.addAndGet(TimeUnit.MILLISECONDS.convert(n, unit))

  /** Set the clock to the specified [Instant]. */
  fun setNow(instant: Instant) = millis.set(instant.toEpochMilli())
}
