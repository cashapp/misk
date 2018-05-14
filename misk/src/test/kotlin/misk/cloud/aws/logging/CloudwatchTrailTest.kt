package misk.cloud.aws.logging

import misk.logging.getLogger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.containsExactly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class CloudwatchTrailTest {
  private class LoggerName1
  private class LoggerName2

  private val eventQueue = ArrayBlockingQueue<CloudwatchLogEvent>(100)
  private val logger1 = getLogger<LoggerName1>()
  private val logger2 = getLogger<LoggerName2>()

  @BeforeEach
  fun setupLogging() {
    eventQueue.clear()
    CloudwatchTrail.registerAppender(CloudwatchLogConfig(), eventQueue)
  }

  @Test fun basicLogging() {
    val startTime = System.currentTimeMillis()

    logger1.error { "this is an error" }
    logger2.warn { "this was a warning" }

    val endTime = System.currentTimeMillis()

    val event1 = eventQueue.poll()
    assertThat(event1.level).isEqualTo("ERROR")
    assertThat(event1.logger).isEqualTo("misk.cloud.aws.logging.CloudwatchTrailTest.LoggerName1")
    assertThat(event1.message).isEqualTo("this is an error")
    assertThat(event1.thread).isEqualTo("main")
    assertThat(event1.context).isEmpty()
    assertThat(event1.throwable_proxy).isNull()
    assertThat(event1.timestamp).isBetween(startTime, endTime)

    val event2 = eventQueue.take()
    assertThat(event2.level).isEqualTo("WARN")
    assertThat(event2.logger).isEqualTo("misk.cloud.aws.logging.CloudwatchTrailTest.LoggerName2")
    assertThat(event2.message).isEqualTo("this was a warning")
    assertThat(event2.thread).isEqualTo("main")
    assertThat(event2.context).isEmpty()
    assertThat(event2.throwable_proxy).isNull()
    assertThat(event2.timestamp).isBetween(startTime, endTime)
  }

  @Test fun capturesMDC() {

    try {
      MDC.put("action", "MyAction")
      MDC.put("caller", "nb")

      logger1.info { "so this happened" }
    } finally {
      MDC.clear()
    }

    val event1 = eventQueue.poll()
    assertThat(event1.level).isEqualTo("INFO")
    assertThat(event1.message).isEqualTo("so this happened")
    assertThat(event1.context).containsExactly(
        "action" to "MyAction",
        "caller" to "nb"
    )
  }

  @Test fun capturesThrowableProxy() {
    logger2.error(IllegalStateException("bad things", IllegalArgumentException("rootCause"))) {
      "something failed"
    }

    val event1 = eventQueue.poll()
    assertThat(event1.level).isEqualTo("ERROR")
    assertThat(event1.message).isEqualTo("something failed")
    assertThat(event1.throwable_proxy).isNotNull()

    val proxy = event1.throwable_proxy!!
    assertThat(proxy.class_name).isEqualTo("java.lang.IllegalStateException")
    assertThat(proxy.message).isEqualTo("bad things")
    assertThat(proxy.stack_trace_elements[0]).startsWith(
        "at misk.cloud.aws.logging.CloudwatchTrailTest.capturesThrowableProxy"
    )

    assertThat(proxy.cause?.class_name).isEqualTo("java.lang.IllegalArgumentException")
    assertThat(proxy.cause?.message).isEqualTo("rootCause")
    assertThat(proxy.cause?.stack_trace_elements?.get(0)).startsWith(
        "at misk.cloud.aws.logging.CloudwatchTrailTest.capturesThrowableProxy"
    )
  }

  @Test fun capturesThreadInfo() {
    val latch = CountDownLatch(1)

    thread(name = "background-thread-yo") {
      logger2.warn { "this occurred elsewhere" }
      latch.countDown()
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()

    val event1 = eventQueue.poll()
    assertThat(event1.level).isEqualTo("WARN")
    assertThat(event1.message).isEqualTo("this occurred elsewhere")
    assertThat(event1.thread).isEqualTo("background-thread-yo")
  }
}