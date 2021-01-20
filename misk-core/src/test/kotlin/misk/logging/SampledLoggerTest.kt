package misk.logging

import ch.qos.logback.classic.Level
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.concurrent.FakeTicker
import misk.sampling.RateLimiter
import misk.sampling.RateLimitingSampler
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.containsExactly
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class SampledLoggerTest {
  @MiskTestModule
  val testModule = Modules.combine(MiskTestingServiceModule(), LogCollectorModule())

  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var fakeTicker: FakeTicker

  @Test
  fun rateLimitedLogger() {
    val rateLimiter = RateLimiter.Factory(fakeTicker, fakeTicker).create(2L)
    val logger = getLogger<LoggingTest>()
    val sampledLogger = logger.sampled(RateLimitingSampler(rateLimiter))

    // clear existing messages
    logCollector.takeEvents(LoggingTest::class)

    // Unsampled logger is not rate-limited
    logger.info("user-id" to "blah1") { "test 1" }
    logger.error(IllegalStateException("failed!"), "user-id" to "blah2") { "test 2" }
    logger.warn("user-id" to "blah3") { "test 3" }

    val events = logCollector.takeEvents(LoggingTest::class)
    assertThat(events).hasSize(3)
    assertThat(events[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.INFO)
      assertThat(it.message).isEqualTo("test 1")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blah1"
      )
    }
    assertThat(events[1]).satisfies {
      assertThat(it.level).isEqualTo(Level.ERROR)
      assertThat(it.message).isEqualTo("test 2")
      assertThat(it.throwableProxy.className).isEqualTo(IllegalStateException::class.qualifiedName)
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blah2"
      )
    }
    assertThat(events[2]).satisfies {
      assertThat(it.level).isEqualTo(Level.WARN)
      assertThat(it.message).isEqualTo("test 3")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blah3"
      )
    }

    // Sampled logger is rate-limited
    sampledLogger.info("user-id" to "blerb1") { "sampled test 1" }
    sampledLogger.error(NullPointerException("failed!"),"user-id" to "blerb2", "context-id" to "111111") { "sampled test 2" }
    sampledLogger.warn("user-id" to "blerb3") { "sampled test 3" }

    val sampledEvents = logCollector.takeEvents(LoggingTest::class)
    assertThat(sampledEvents).hasSize(2)
    assertThat(sampledEvents[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.INFO)
      assertThat(it.message).isEqualTo("sampled test 1")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blerb1"
      )
    }
    assertThat(sampledEvents[1]).satisfies {
      assertThat(it.level).isEqualTo(Level.ERROR)
      assertThat(it.message).isEqualTo("sampled test 2")
      assertThat(it.throwableProxy.className).isEqualTo(NullPointerException::class.qualifiedName)
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blerb2",
        "context-id" to "111111"
      )
    }

    // Wait 1 second
    fakeTicker.sleepMs(1000L)

    sampledLogger.warn("user-id" to "blerb4") { "sampled test 4" }
    val sampledEvents2 = logCollector.takeEvents(LoggingTest::class)
    assertThat(sampledEvents2).hasSize(1)
    assertThat(sampledEvents2[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.WARN)
      assertThat(it.message).isEqualTo("sampled test 4")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blerb4"
      )
    }
  }
}
