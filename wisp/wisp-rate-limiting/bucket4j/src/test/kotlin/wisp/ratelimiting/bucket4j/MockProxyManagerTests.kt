package wisp.ratelimiting.bucket4j

import io.github.bucket4j.mock.ProxyManagerMock

internal class MockProxyManagerTests : AbstractBucket4jRateLimiterTest<String>() {
  private val timeMeter = ClockTimeMeter(fakeClock)
  private val proxyManager = ProxyManagerMock<String>(timeMeter)
  override val rateLimiter = Bucket4jRateLimiter(proxyManager, fakeClock)
}
