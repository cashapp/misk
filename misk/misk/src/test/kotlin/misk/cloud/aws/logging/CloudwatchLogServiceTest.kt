package misk.cloud.aws.logging

import com.amazonaws.SdkClientException
import com.amazonaws.services.logs.AWSLogs
import com.amazonaws.services.logs.model.InputLogEvent
import com.amazonaws.services.logs.model.InvalidSequenceTokenException
import com.amazonaws.services.logs.model.PutLogEventsRequest
import com.amazonaws.services.logs.model.PutLogEventsResult
import com.squareup.moshi.Moshi
import misk.mockito.Mockito.captor
import misk.mockito.Mockito.mock
import misk.mockito.Mockito.whenever
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

internal class CloudwatchLogServiceTest {
  private lateinit var service: CloudwatchLogService
  private lateinit var clock: FakeClock
  private lateinit var events: BlockingQueue<CloudwatchLogEvent>
  private lateinit var logs: AWSLogs

  private val maxBatchByteSize = 1024 * 5

  @BeforeEach
  fun initService() {
    logs = mock()
    events = ArrayBlockingQueue(100)
    clock = FakeClock()
    service = CloudwatchLogService(
        events = events,
        appName = "my-app",
        clock = clock,
        logs = logs,
        moshi = Moshi.Builder().build(),
        config = CloudwatchLogConfig(
            max_batch_size = 5,
            max_flush_delay = Duration.ofSeconds(1)
        ),
        maxBatchByteSize = maxBatchByteSize)
  }

  @Test fun flushedWhenExceedsBatchItemCount() {
    whenever(logs.putLogEvents(any()))
        .thenReturn(PutLogEventsResult().withNextSequenceToken("token-1"))

    var timestamp: Long = 0

    for (i in 0..2) {
      events.put(CloudwatchLogEvent(
          level = "INFO",
          message = "this happened $i",
          logger = "from-logger",
          timestamp = timestamp
      ))
      timestamp += 500
    }

    // Should not push since we don't exceed the batch size, batch byte size, or flush time
    while (events.isNotEmpty()) service.runOnce()

    for (i in 0..4) {
      events.put(CloudwatchLogEvent(
          level = "WARN",
          message = "this happened next $i",
          logger = "other-logger",
          timestamp = timestamp
      ))
      timestamp += 1000
    }

    // Should send the first batch
    while (events.isNotEmpty()) service.runOnce()

    val putLogRequest = captor<PutLogEventsRequest>()
    verify(logs, times(1)).putLogEvents(putLogRequest.capture())

    assertThat(putLogRequest.value.logGroupName).isEqualTo("my-app")
    assertThat(putLogRequest.value.logStreamName).isEqualTo("")
    assertThat(putLogRequest.value.sequenceToken).isEqualTo("")
    assertThat(putLogRequest.value.logEvents).containsExactly(
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"from-logger","message":"this happened 0","timestamp":0}""")
            .withTimestamp(0),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"from-logger","message":"this happened 1","timestamp":500}""")
            .withTimestamp(500),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"from-logger","message":"this happened 2","timestamp":1000}""")
            .withTimestamp(1000),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"WARN","logger":"other-logger","message":"this happened next 0","timestamp":1500}""")
            .withTimestamp(1500),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"WARN","logger":"other-logger","message":"this happened next 1","timestamp":2500}""")
            .withTimestamp(2500)
    )

  }

  @Test fun flushesWhenExceedsBatchByteSize() {
    whenever(logs.putLogEvents(any()))
        .thenReturn(PutLogEventsResult().withNextSequenceToken("token-1"))
        .thenReturn(PutLogEventsResult().withNextSequenceToken("token-2"))

    // Put a few items, one of which causes us to trip over the max byte size for a batch
    events.put(CloudwatchLogEvent(
        level = "INFO",
        message = "this happened so long ago",
        logger = "from-logger",
        timestamp = 175060
    ))

    events.put(CloudwatchLogEvent(
        level = "WARN",
        message = "just prior to exceeding",
        logger = "from-other-logger",
        timestamp = 172066
    ))

    val bigMessage = "ABCD".repeat(
        (maxBatchByteSize / 4) - (CloudwatchLogService.LOG_ENTRY_BYTE_OVERHEAD * 3 + 10))
    events.put(CloudwatchLogEvent(
        level = "ERROR",
        message = bigMessage,
        logger = "from-big-logger",
        timestamp = 176049
    ))

    while (events.isNotEmpty()) service.runOnce()

    // Should have sent the first two, since the third trips over the max batch size
    val putLogRequest = captor<PutLogEventsRequest>()
    verify(logs, times(1)).putLogEvents(putLogRequest.capture())
  }

  @Test fun flushesWhenPastTime() {
    whenever(logs.putLogEvents(any()))
        .thenReturn(PutLogEventsResult().withNextSequenceToken("token-1"))
        .thenReturn(PutLogEventsResult().withNextSequenceToken("token-2"))

    // Put a few items, one of which causes us to trip over the max byte size for a batch
    events.put(CloudwatchLogEvent(
        level = "INFO",
        message = "this happened so long ago",
        logger = "from-logger",
        timestamp = 175060
    ))

    events.put(CloudwatchLogEvent(
        level = "WARN",
        message = "another thing that happened",
        logger = "from-other-logger",
        timestamp = 172066
    ))

    while (events.isNotEmpty()) service.runOnce()

    // Advance the time past the flush interval and add another event
    clock.add(Duration.ofSeconds(2))
    events.put(CloudwatchLogEvent(
        level = "ERROR",
        message = "just prior to flush",
        logger = "from-third-logger",
        timestamp = 172075
    ))

    // Should flush everything (verified below)
    while (events.isNotEmpty()) service.runOnce()

    // Add another event
    events.put(CloudwatchLogEvent(
        level = "ERROR",
        message = "fourth event",
        logger = "from-third-logger",
        timestamp = 172910
    ))
    while (events.isNotEmpty()) service.runOnce()

    // Advance past the flush interval but don't add another event. Should flush
    clock.add(Duration.ofSeconds(1).plus(Duration.ofMillis(5)))
    service.runOnce()

    // Should have flushed twice, each time we went past the flush interval
    val putLogRequest = captor<PutLogEventsRequest>()
    verify(logs, times(2)).putLogEvents(putLogRequest.capture())
    assertThat(putLogRequest.allValues[0].logGroupName).isEqualTo("my-app")
    assertThat(putLogRequest.allValues[0].logStreamName).isEqualTo("")
    assertThat(putLogRequest.allValues[0].sequenceToken).isEqualTo("")
    assertThat(putLogRequest.allValues[0].logEvents).containsExactly(
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"WARN","logger":"from-other-logger","message":"another thing that happened","timestamp":172066}""")
            .withTimestamp(172066),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"ERROR","logger":"from-third-logger","message":"just prior to flush","timestamp":172075}""")
            .withTimestamp(172075),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"from-logger","message":"this happened so long ago","timestamp":175060}""")
            .withTimestamp(175060)
    )

    assertThat(putLogRequest.allValues[1].logGroupName).isEqualTo("my-app")
    assertThat(putLogRequest.allValues[1].logStreamName).isEqualTo("")
    assertThat(putLogRequest.allValues[1].sequenceToken).isEqualTo("token-1")
    assertThat(putLogRequest.allValues[1].logEvents).containsExactly(
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"ERROR","logger":"from-third-logger","message":"fourth event","timestamp":172910}""")
            .withTimestamp(172910)
    )
  }

  @Test fun retriesWithNewTokenIfSequenceTokenIsBad() {
    whenever(logs.putLogEvents(any()))
        .thenThrow(
            InvalidSequenceTokenException("missing token").withExpectedSequenceToken("new token"))
        .thenReturn(PutLogEventsResult().withNextSequenceToken("token-2"))

    for (i in 0..5) {
      events.put(CloudwatchLogEvent(
          level = "INFO",
          message = "this is message $i",
          logger = "my-logger",
          timestamp = 10000L * i
      ))
    }

    while (events.isNotEmpty()) service.runOnce()

    val putLogRequest = captor<PutLogEventsRequest>()
    verify(logs, times(2)).putLogEvents(putLogRequest.capture())

    // First should use the original token (a blank)
    assertThat(putLogRequest.allValues[0].logGroupName).isEqualTo("my-app")
    assertThat(putLogRequest.allValues[0].logStreamName).isEqualTo("")
    assertThat(putLogRequest.allValues[0].sequenceToken).isEqualTo("")
    assertThat(putLogRequest.allValues[0].logEvents).containsExactly(
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 0","timestamp":0}""")
            .withTimestamp(0),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 1","timestamp":10000}""")
            .withTimestamp(10000),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 2","timestamp":20000}""")
            .withTimestamp(20000),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 3","timestamp":30000}""")
            .withTimestamp(30000),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 4","timestamp":40000}""")
            .withTimestamp(40000)
    )

    // Second should use the token returned in the exception
    assertThat(putLogRequest.allValues[1].logGroupName).isEqualTo("my-app")
    assertThat(putLogRequest.allValues[1].logStreamName).isEqualTo("")
    assertThat(putLogRequest.allValues[1].sequenceToken).isEqualTo("new token")
    assertThat(putLogRequest.allValues[1].logEvents).containsExactly(
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 0","timestamp":0}""")
            .withTimestamp(0),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 1","timestamp":10000}""")
            .withTimestamp(10000),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 2","timestamp":20000}""")
            .withTimestamp(20000),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 3","timestamp":30000}""")
            .withTimestamp(30000),
        InputLogEvent()
            .withMessage(
                """{"context":{},"level":"INFO","logger":"my-logger","message":"this is message 4","timestamp":40000}""")
            .withTimestamp(40000)
    )
  }

  @Test fun retriesOnSdkException() {
    whenever(logs.putLogEvents(any()))
        .thenThrow(SdkClientException("attempt one failed"))
        .thenThrow(SdkClientException("attempt two failed"))
        .thenReturn(PutLogEventsResult()
            .withNextSequenceToken("my-token"))

    for (i in 0..5) {
      events.put(CloudwatchLogEvent(
          level = "INFO",
          message = "this is message $i",
          logger = "my-logger",
          timestamp = 10000L * i
      ))
    }

    while (events.isNotEmpty()) service.runOnce()

    // The first few puts failed, but should have been retried
    val putLogRequest = captor<PutLogEventsRequest>()
    verify(logs, times(3)).putLogEvents(putLogRequest.capture())

    // Same request should be used each time
    assertThat(putLogRequest.allValues[0]).isEqualTo(putLogRequest.value)
    assertThat(putLogRequest.allValues[1]).isEqualTo(putLogRequest.value)
    assertThat(putLogRequest.allValues[2]).isEqualTo(putLogRequest.value)
  }

  @Test fun failsAfter5Retries() {
    whenever(logs.putLogEvents(any()))
        .thenThrow(SdkClientException("attempt one failed"))
        .thenThrow(SdkClientException("attempt two failed"))
        .thenThrow(SdkClientException("attempt three failed"))
        .thenThrow(SdkClientException("attempt four failed"))
        .thenThrow(SdkClientException("attempt five failed"))

    for (i in 0..5) {
      events.put(CloudwatchLogEvent(
          level = "INFO",
          message = "this is message $i",
          logger = "my-logger",
          timestamp = 10000L * i
      ))
    }

    while (events.isNotEmpty()) service.runOnce()

    // All attempts to put should fail
    val putLogRequest = captor<PutLogEventsRequest>()
    verify(logs, times(5)).putLogEvents(putLogRequest.capture())

    // Same request should be used each time
    assertThat(putLogRequest.allValues[0]).isEqualTo(putLogRequest.value)
    assertThat(putLogRequest.allValues[1]).isEqualTo(putLogRequest.value)
    assertThat(putLogRequest.allValues[2]).isEqualTo(putLogRequest.value)
    assertThat(putLogRequest.allValues[3]).isEqualTo(putLogRequest.value)
    assertThat(putLogRequest.allValues[4]).isEqualTo(putLogRequest.value)
  }
}