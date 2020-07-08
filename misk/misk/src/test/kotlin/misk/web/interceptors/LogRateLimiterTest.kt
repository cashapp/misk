package misk.web.interceptors

import com.google.common.base.Ticker
import misk.ServiceManagerModule
import misk.concurrent.Sleeper
import misk.inject.KAbstractModule
import misk.ratelimit.FakeTicker
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.interceptors.LogRateLimiter.LogBucketId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class LogRateLimiterTest {
  @MiskTestModule val module =
    TestModule()

  @Inject lateinit var logRateLimiter: LogRateLimiter
  @Inject lateinit var fakeTicker: FakeTicker

  @Test
  fun tryAcquire() {
    val bucketId = LogBucketId("TestAction", false)
    assertThat(
      logRateLimiter.tryAcquire(bucketId, 2L)).isTrue()
    assertThat(
      logRateLimiter.tryAcquire(bucketId, 2L)).isTrue()
    assertThat(
      logRateLimiter.tryAcquire(bucketId, 2L)).isFalse()

    // Wait 1 second
    fakeTicker.sleepMs(1000L)
    assertThat(logRateLimiter.tryAcquire(bucketId, 2L)).isTrue()
  }

  @Test
  fun noRateLimiting() {
    assertThat(
      logRateLimiter.tryAcquire(LogBucketId("NoLogRateLimitingTestAction", false), 0L)
    ).isTrue()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(ServiceManagerModule())
      bind<Ticker>().to<FakeTicker>()
      bind<Sleeper>().to<FakeTicker>()
    }
  }
}
