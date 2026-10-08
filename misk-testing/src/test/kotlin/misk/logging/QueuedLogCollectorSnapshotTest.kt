package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyVO
import ch.qos.logback.core.read.ListAppender
import com.google.common.testing.GcFinalization
import java.lang.ref.WeakReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class QueuedLogCollectorSnapshotTest {
  private val collector = QueuedLogCollector()
  private val logger = LoggerFactory.getLogger(javaClass) as Logger
  private val previousLevel = logger.level

  @BeforeEach
  fun start() {
    logger.level = Level.INFO
    collector.startUp()
  }

  @AfterEach
  fun stop() {
    collector.shutDown()
    collector.reset()
    logger.level = previousLevel
  }

  @Test
  fun parameterizedMessagesDetachArgumentsWithoutMutatingOtherAppenders() {
    val otherAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(otherAppender)
    try {
      val fixture = Fixture("fixture")
      logger.info("Started {}", fixture)
      fixture.name = "changed"

      val event = collector.takeEvent()
      assertThat(event.message).isEqualTo("Started {}")
      assertThat(event.formattedMessage).isEqualTo("Started fixture")
      assertThat(event.argumentArray).isNull()
      assertThat(otherAppender.list.single().argumentArray.single()).isSameAs(fixture)
    } finally {
      logger.detachAppender(otherAppender)
      otherAppender.stop()
    }
  }

  @Test
  fun preservesArrayFormattingNullsAndEscapedPlaceholders() {
    logger.info("arrays {} {} null {} escaped \\{}", intArrayOf(1, 2), arrayOf("a", "b"), null)

    val event = collector.takeEvent()
    assertThat(event.message).isEqualTo("arrays {} {} null {} escaped \\{}")
    assertThat(event.formattedMessage).isEqualTo("arrays [1, 2] [a, b] null null escaped \\{}")
    assertThat(event.argumentArray).isNull()
  }

  @Test
  fun doesNotRenderArgumentsAgainOrRenderUnusedArguments() {
    val used = CountingValue()
    val unused = CountingValue()
    logger.info("value {}", used, unused)

    val event = collector.takeEvent()
    assertThat(event.formattedMessage).isEqualTo("value diagnostic")
    assertThat(used.renderCount).isEqualTo(1)
    assertThat(unused.renderCount).isZero()
  }

  @Test
  fun capturesStructuredValuesAndThrowableDetails() {
    val fixture = Fixture("fixture")
    val failure =
      FixtureException(fixture).apply {
        initCause(IllegalArgumentException("cause"))
        addSuppressed(IllegalStateException("suppressed"))
      }
    MDC.put("snapshot-test", "value")
    try {
      logger.atWarn().addKeyValue("fixture", fixture).setCause(failure).log("Failed {}", fixture)
    } finally {
      MDC.remove("snapshot-test")
    }
    fixture.name = "changed"

    val event = collector.takeEvent()
    assertThat(event.loggerName).isEqualTo(logger.name)
    assertThat(event.level).isEqualTo(Level.WARN)
    assertThat(event.threadName).isEqualTo(Thread.currentThread().name)
    assertThat(event.timeStamp).isPositive()
    assertThat(event.formattedMessage).isEqualTo("Failed fixture")
    assertThat(event.mdcPropertyMap).containsEntry("snapshot-test", "value")
    assertThat(event.keyValuePairs.single().key).isEqualTo("fixture")
    assertThat(event.keyValuePairs.single().value).isEqualTo("fixture")
    assertThat(event.throwableProxy).isInstanceOf(ThrowableProxyVO::class.java)
    assertThat(event.throwableProxy.message).isEqualTo("failure")
    assertThat(event.throwableProxy.cause.message).isEqualTo("cause")
    assertThat(event.throwableProxy.suppressed.single().message).isEqualTo("suppressed")
    assertThat(event.throwableProxy.stackTraceElementProxyArray).isNotEmpty()
  }

  @Test
  fun queuedEventsDoNotKeepFixtureGraphsAlive() {
    val fixture = logFixture()
    GcFinalization.awaitClear(fixture)
    assertThat(collector.takeEvent().formattedMessage).isEqualTo("Failed fixture")
  }

  private fun logFixture(): WeakReference<Fixture> {
    val fixture = Fixture("fixture")
    logger.atWarn().addKeyValue("fixture", fixture).setCause(FixtureException(fixture)).log("Failed {}", fixture)
    return WeakReference(fixture)
  }

  private class CountingValue {
    var renderCount = 0

    override fun toString(): String {
      renderCount++
      return "diagnostic"
    }
  }

  private class Fixture(var name: String) {
    override fun toString() = name
  }

  private class FixtureException(val fixture: Fixture) : IllegalStateException("failure")
}
