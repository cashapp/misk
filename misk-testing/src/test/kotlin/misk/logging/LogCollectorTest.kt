package misk.logging

import ch.qos.logback.classic.Level
import com.google.inject.Module
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import wisp.logging.getLogger
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class LogCollectorTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = Modules.combine(
    MiskTestingServiceModule(),
    LogCollectorModule()
  )

  @Inject lateinit var logCollector: LogCollector

  @Test
  fun happyPath() {
    val logger = getLogger<LogCollectorTest>()

    logger.info("this is a log message!")
    assertThat(logCollector.takeMessages()).containsExactly("this is a log message!")

    logger.info("another log message")
    logger.info("and a third!")
    assertThat(logCollector.takeMessages()).containsExactly("another log message", "and a third!")
  }

  @Test
  fun filterByLevel() {
    val logger = getLogger<LogCollectorTest>()

    logger.debug("this is DEBUG.")
    logger.info("this is INFO.")
    logger.warn("this is WARN!")
    assertThat(logCollector.takeMessages(minLevel = Level.INFO))
      .containsExactly("this is INFO.", "this is WARN!")
  }

  @Test
  fun filterByLogger() {
    val testLogger = getLogger<LogCollectorTest>()
    val moduleLogger = getLogger<LogCollectorModule>()

    testLogger.info("this is from the test logger")
    moduleLogger.info("this is from the module logger")
    assertThat(logCollector.takeMessages(loggerClass = LogCollectorModule::class))
      .containsExactly("this is from the module logger")
  }

  @Test
  fun filterByPattern() {
    val logger = getLogger<LogCollectorTest>()

    logger.info("this matches the pattern")
    logger.info("this does not match the pattern")
    assertThat(logCollector.takeMessages(pattern = Regex("m[a-z]tch[a-z]s")))
      .containsExactly("this matches the pattern")
  }

  @Test
  fun takeConsumesUnmatched() {
    val logger = getLogger<LogCollectorTest>()

    logger.info("this is a log message!")
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR)).isEmpty()
    assertThat(logCollector.takeMessages()).isEmpty()
  }

  @Test
  fun canTakeWithoutConsumingUnmatchedLogs() {
    val logger = getLogger<LogCollectorTest>()
    val logger2 = getLogger<LogCollectorModule>()

    logger.info("A thing happened")
    logger2.info("Another thing happened")

    // We can collect messages from different log sources.
    assertThat(logCollector.takeMessages(LogCollectorTest::class, consumeUnmatchedLogs = false))
      .containsExactly("A thing happened")
    assertThat(logCollector.takeMessages(LogCollectorModule::class, consumeUnmatchedLogs = false))
      .containsExactly("Another thing happened")
    assertThat(logCollector.takeMessages(consumeUnmatchedLogs = false)).isEmpty()

    // We can collect messages of different error levels.
    logger.info { "this is a log message!" }
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR, consumeUnmatchedLogs = false))
      .isEmpty()
    assertThat(logCollector.takeMessages(consumeUnmatchedLogs = false))
      .containsExactly("this is a log message!")
    assertThat(logCollector.takeMessages(consumeUnmatchedLogs = false)).isEmpty()

    // We can collect messages matching certain patterns.
    logger.info { "hit by pattern match" }
    logger.info { "missed by pattern match"}
    assertThat(logCollector.takeMessages(pattern = Regex("hit.*"), consumeUnmatchedLogs = false))
      .containsExactly("hit by pattern match")
    assertThat(logCollector.takeMessages(consumeUnmatchedLogs = false))
      .containsExactly("missed by pattern match")
  }

  @Test
  fun takeTimeout() {
    val exception = assertFailsWith<IllegalArgumentException> {
      logCollector.takeEvent()
    }
    assertThat(exception).hasMessage("no events to take!")
  }

  /** Confirm that take works even if the log isn't made until after takeEvent() is called. */
  @Test
  fun takeWaits() {
    val logger = getLogger<LogCollectorTest>()

    val thread = object : Thread() {
      override fun run() {
        sleep(100)
        logger.info("this is a log message!")
      }
    }
    thread.start()

    assertThat(logCollector.takeMessage()).isEqualTo("this is a log message!")
  }
}
