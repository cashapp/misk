package misk.testing

import ch.qos.logback.classic.Level
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import wisp.logging.getLogger
import javax.inject.Inject

@MiskTest(startService = true)
class LogLevelExtensionTest {

  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(LogCollectorModule())
    }
  }

  @Inject lateinit var logCollector: LogCollector

  @Test fun test() {
    logMessages()
    assertThat(logCollector.takeMessages(minLevel = Level.ALL)).containsExactly(
      "this is INFO.", "this is WARN.", "this is ERROR."
    )

  }

  @LogLevel(level = LogLevel.Level.DEBUG)
  @Test fun levelDebug() {
    logMessages()
    assertThat(logCollector.takeMessages(minLevel = Level.ALL)).containsExactly(
      "this is DEBUG.", "this is INFO.", "this is WARN.", "this is ERROR."
    )
  }

  @LogLevel(level = LogLevel.Level.INFO)
  @Test fun levelInfo() {
    logMessages()
    assertThat(logCollector.takeMessages(minLevel = Level.ALL)).containsExactly(
      "this is INFO.", "this is WARN.", "this is ERROR."
    )
  }

  @LogLevel(level = LogLevel.Level.WARN)
  @Test fun levelWarn() {
    logMessages()
    assertThat(logCollector.takeMessages(minLevel = Level.ALL)).containsExactly(
      "this is WARN.", "this is ERROR."
    )
  }

  @LogLevel(level = LogLevel.Level.ERROR)
  @Test fun levelError() {
    logMessages()
    assertThat(logCollector.takeMessages(minLevel = Level.ALL)).containsExactly(
      "this is ERROR."
    )
  }


  @LogLevel(level = LogLevel.Level.ERROR)
  @Nested
  inner class `you can annotate the test` {
    @Test fun levelError() {
      logMessages()
      assertThat(logCollector.takeMessages(minLevel = Level.ALL)).containsExactly(
        "this is ERROR."
      )
    }
  }

  private fun logMessages() {
    logger.debug("this is DEBUG.")
    logger.info("this is INFO.")
    logger.warn("this is WARN.")
    logger.error("this is ERROR.")
  }

  companion object {
    val logger = getLogger<LogLevelExtensionTest>()
  }

}
