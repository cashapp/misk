package misk.concurrent

import com.google.common.base.Ticker
import java.time.Duration
import java.util.concurrent.TimeUnit
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.TestFixture

@Singleton
class FakeTicker @Inject constructor() : Ticker(), Sleeper, TestFixture {
  private var nowNs = 0L

  val nowMs: Long
    get() = TimeUnit.NANOSECONDS.toMillis(nowNs)

  override fun read() = nowNs

  override fun sleep(duration: Duration) {
    nowNs += duration.toNanos()
  }

  fun sleepMs(durationMs: Long) {
    nowNs += TimeUnit.MILLISECONDS.toNanos(durationMs)
  }

  override fun reset() {
    nowNs = 0L
  }
}
