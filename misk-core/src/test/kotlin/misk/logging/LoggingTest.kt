package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.containsExactly
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import javax.inject.Inject

@MiskTest
class LoggingTest {
  @MiskTestModule
  val testModule = Modules.combine(MiskTestingServiceModule(), LogCollectorModule())

  @Inject private lateinit var logCollector: LogCollector

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
}
