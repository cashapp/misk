package misk.web.interceptors

import com.google.common.base.Ticker
import misk.ServiceManagerModule
import misk.concurrent.Sleeper
import misk.inject.KAbstractModule
import misk.ratelimit.FakeTicker
import misk.web.interceptors.LogRateLimiter.LogBucketId
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
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
    val bucketId = LogBucketId("caller", TestAction::class, false)
    assertThat(
      logRateLimiter.tryAcquire(bucketId)).isTrue()
    assertThat(
      logRateLimiter.tryAcquire(bucketId)).isTrue()
    assertThat(
      logRateLimiter.tryAcquire(bucketId)).isFalse()

    // Wait 1 second
    fakeTicker.sleepMs(1000L)
    assertThat(logRateLimiter.tryAcquire(bucketId)).isTrue()
  }

  @Test
  fun noRateLimiting() {
    assertThat(
      logRateLimiter.tryAcquire(LogBucketId("caller", NoLogRateLimitingTestAction::class, false))
    ).isTrue()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(ServiceManagerModule())
      bind<Ticker>().to<FakeTicker>()
      bind<Sleeper>().to<FakeTicker>()
    }
  }

  @LogRequestResponse(rateLimiting = 2L, errorRateLimiting = 2L, sampling = 1.0, includeBody = false)
  class TestAction: WebAction

  @LogRequestResponse(sampling = 1.0, includeBody = false)
  class NoLogRateLimitingTestAction: WebAction
}
