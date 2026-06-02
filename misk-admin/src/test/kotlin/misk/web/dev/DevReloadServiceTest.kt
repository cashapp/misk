package misk.web.dev

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import misk.time.FakeClock
import org.junit.jupiter.api.Test

internal class ReloadSignalServiceTest {
  private val clock = FakeClock()
  private val service = ReloadSignalService(clock)

  @Test
  fun startSetsTimestamp() {
    service.startAsync().awaitRunning()

    assertEquals(clock.instant(), service.lastLoadTimestamp)

    assertFalse(service.awaitShutdown(10))

    service.stopAsync().awaitTerminated()
  }

  @Test
  fun stopSignalsShutdown() {
    service.startAsync().awaitRunning()

    assertFalse(service.awaitShutdown(10))

    service.stopAsync().awaitTerminated()

    assertTrue(service.awaitShutdown(10))
  }
}
