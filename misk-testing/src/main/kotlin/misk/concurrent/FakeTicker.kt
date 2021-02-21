package misk.concurrent

import com.google.common.base.Ticker
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeTicker @Inject constructor() : Ticker(), Sleeper {
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
}
