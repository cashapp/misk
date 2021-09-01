package wisp.logging

import ch.qos.logback.classic.Level
import org.assertj.core.api.Assertions.`as`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class LogCollectorTest {
  var wispQueuedLogCollector = WispQueuedLogCollector()

  private var logCollector: LogCollector = wispQueuedLogCollector

  @BeforeEach
  fun beforeEach() {
    wispQueuedLogCollector.startUp()
  }

  @AfterEach
  fun afterEach() {
    wispQueuedLogCollector.shutDown()
  }

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
    val anotherLogger = getLogger<LoggingDummyClass>()

    testLogger.info("this is from the test logger")
    anotherLogger.info("this is from the another logger")
    assertThat(logCollector.takeMessages(loggerClass = LoggingDummyClass::class))
      .containsExactly("this is from the another logger")
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
  fun canTakeWithoutConsumingUnmatchedLogs() {
    val logger = getLogger<LogCollectorTest>()
    val logger2 = getLogger<LoggingDummyClass>()

    logger.info("A thing happened")
    logger2.info("Another thing happened")

    // We can collect messages from different log sources.
    assertThat(logCollector.takeMessages(LogCollectorTest::class)).containsExactly("A thing happened")
    assertThat(logCollector.takeMessages(LoggingDummyClass::class)).containsExactly("Another thing happened")
    assertThat(logCollector.takeMessages()).isEmpty()

    // We can collect messages of different error levels.
    logger.info { "this is a log message!" }
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR)).isEmpty()
    assertThat(logCollector.takeMessages()).containsExactly("this is a log message!")
    assertThat(logCollector.takeMessages()).isEmpty()

    // We can collect messages matching certain patterns.
    logger.info { "hit by pattern match" }
    logger.info { "missed by pattern match"}
    assertThat(logCollector.takeMessages(pattern = Regex("hit.*")))
      .containsExactly("hit by pattern match")
    assertThat(logCollector.takeMessages()).containsExactly("missed by pattern match")
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

private class LoggingDummyClass
