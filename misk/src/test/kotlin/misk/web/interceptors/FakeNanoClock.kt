package misk.web.interceptors

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Alternative implementation of FakeClock with nanosecond precision. This implementation is
 * slower, because it uses synchronization over an AtomicLong, but that's fine.
 */
class FakeNanoClock(
  epochMillis: Long = Instant.parse("2018-01-01T00:00:00Z").toEpochMilli(),
  private val zone: ZoneId = ZoneId.of("UTC")
) : Clock() {

  private var now: Instant = Instant.ofEpochMilli(epochMillis)

  override fun getZone(): ZoneId = zone

  override fun withZone(zone: ZoneId): Clock = FakeNanoClock(now.toEpochMilli(), zone)

  @Synchronized
  override fun instant(): Instant = now.atZone(zone).toInstant()

  @Synchronized
  fun add(d: Duration) {
    now = now.plus(d)
  }

  @Synchronized
  fun setNow(instant: Instant) {
    now = instant
  }
}
