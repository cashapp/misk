package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
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
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import javax.inject.Inject

@MiskTest(startService = true)
class LoggingTest {
  @MiskTestModule
  val testModule = Modules.combine(MiskTestingServiceModule(), LogCollectorModule())

  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var fakeTicker: FakeTicker

  private val logger = getLogger<LoggingTest>()
  private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

  @Test
  fun loggerNameIsBasedOnTheOuterClass() {
    assertThat(logger.name).isEqualTo("misk.logging.LoggingTest")
  }

  @Test
  fun loggingWithTags() {
    // clear existing messages
    logCollector.takeEvents(LoggingTest::class)

    // Log without tags
    logger.error(IllegalStateException("failed!")) { "untagged error" }
    logger.info { "untagged info" }

    val untaggedEvents = logCollector.takeEvents(LoggingTest::class)
    assertThat(untaggedEvents).hasSize(2)
    assertThat(untaggedEvents[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.ERROR)
      assertThat(it.message).isEqualTo("untagged error")
      assertThat(it.throwableProxy.className).isEqualTo(IllegalStateException::class.qualifiedName)
      assertThat(it.mdcPropertyMap).isEmpty()
    }
    assertThat(untaggedEvents[1]).satisfies {
      assertThat(it.level).isEqualTo(Level.INFO)
      assertThat(it.message).isEqualTo("untagged info")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).isEmpty()
    }

    // Log with tags
    logger.info("user-id" to "blerb", "alias-id" to "d6F1EF53") { "tagged info" }
    logger.error(NullPointerException("failed!"), "sample-size" to "200") { "tagged error" }

    val taggedEvents = logCollector.takeEvents(LoggingTest::class)
    assertThat(taggedEvents).hasSize(2)
    assertThat(taggedEvents[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.INFO)
      assertThat(it.message).isEqualTo("tagged info")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
          "user-id" to "blerb",
          "alias-id" to "d6F1EF53"
      )
    }
    assertThat(taggedEvents[1]).satisfies {
      assertThat(it.level).isEqualTo(Level.ERROR)
      assertThat(it.message).isEqualTo("tagged error")
      assertThat(it.throwableProxy.className).isEqualTo(NullPointerException::class.qualifiedName)
      assertThat(it.mdcPropertyMap).containsExactly(
          "sample-size" to "200"
      )
    }

    // Establish external MDC, override with per-log message tags
    try {
      MDC.put("user-id", "inherited-external")
      MDC.put("context-id", "111111")
      logger.warn("context-id" to "overridden") { "inherited warn" }
      logger.info { "uses default tags" }
    } finally {
      MDC.clear()
    }

    val defaultTaggedEvents = logCollector.takeEvents(LoggingTest::class)
    assertThat(defaultTaggedEvents).hasSize(2)
    assertThat(defaultTaggedEvents[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.WARN)
      assertThat(it.message).isEqualTo("inherited warn")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
          "user-id" to "inherited-external",
          "context-id" to "overridden"
      )
    }
    assertThat(defaultTaggedEvents[1]).satisfies {
      assertThat(it.level).isEqualTo(Level.INFO)
      assertThat(it.message).isEqualTo("uses default tags")
      assertThat(it.mdcPropertyMap).containsExactly(
          "user-id" to "inherited-external",
          "context-id" to "111111"
      )
    }
  }

  @Test
  fun sampledLogging() {
    // clear existing messages
    logCollector.takeEvents(LoggingTest::class)

    val rateLimiter = RateLimiter.Factory(fakeTicker, fakeTicker).create(2L)
    val sampler = RateLimitingSampler(rateLimiter)

    logger.info(sampler, "user-id" to "blerb1") { "test 1" }
    logger.error(sampler, NullPointerException("failed!"),"user-id" to "blerb2", "context-id" to "111111") { "test 2" }
    logger.warn(sampler, "user-id" to "blerb3") { "test 3" }

    val events = logCollector.takeEvents(LoggingTest::class)
    assertThat(events).hasSize(2)
    assertThat(events[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.INFO)
      assertThat(it.message).isEqualTo("test 1")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blerb1"
      )
    }
    assertThat(events[1]).satisfies {
      assertThat(it.level).isEqualTo(Level.ERROR)
      assertThat(it.message).isEqualTo("test 2")
      assertThat(it.throwableProxy.className).isEqualTo(NullPointerException::class.qualifiedName)
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blerb2",
        "context-id" to "111111"
      )
    }

    // Wait 1 second
    fakeTicker.sleepMs(1000L)

    logger.warn(sampler, "user-id" to "blerb4") { "test 4" }
    val events2 = logCollector.takeEvents(LoggingTest::class)
    assertThat(events2).hasSize(1)
    assertThat(events2[0]).satisfies {
      assertThat(it.level).isEqualTo(Level.WARN)
      assertThat(it.message).isEqualTo("test 4")
      assertThat(it.throwableProxy).isNull()
      assertThat(it.mdcPropertyMap).containsExactly(
        "user-id" to "blerb4"
      )
    }
  }
}
