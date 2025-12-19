package misk.web.dev

import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS

@Singleton
internal class ReloadSignalService @Inject constructor(private val clock: Clock) : AbstractIdleService() {
  @Volatile var lastLoadTimestamp: Instant = Instant.EPOCH
  @Volatile private var shutdownLatch = CountDownLatch(1)

  override fun startUp() {
    lastLoadTimestamp = clock.instant()
    shutdownLatch = CountDownLatch(1)
  }

  override fun shutDown() {
    shutdownLatch.countDown()
  }

  fun awaitShutdown(timeoutMs: Long) = shutdownLatch.await(timeoutMs, MILLISECONDS)
}
