package misk.cloud.aws.logging

import com.amazonaws.services.logs.AWSLogs
import com.amazonaws.services.logs.model.InputLogEvent
import com.amazonaws.services.logs.model.InvalidSequenceTokenException
import com.amazonaws.services.logs.model.PutLogEventsRequest
import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.backoff.ExponentialBackoff
import misk.backoff.retry
import misk.moshi.adapter
import java.time.Clock
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

@Singleton
internal class CloudwatchLogService(
  private val events: BlockingQueue<CloudwatchLogEvent>,
  moshi: Moshi,
  config: CloudwatchLogConfig,
  logs: AWSLogs,
  appName: String,
  clock: Clock,
  maxBatchByteSize: Int = DEFAULT_MAX_BATCH_BYTES_SIZE
) : AbstractIdleService(), Runnable {
  private val running = AtomicBoolean()
  private val eventAdapter = moshi.adapter<CloudwatchLogEvent>()
  private val thread = Thread(null, this, "cloudwatch-log-sender-$appName", 0)
  private val client = LogClient(config, logs, appName, "", clock)
  private val batch = EventBatch(config.max_batch_size, maxBatchByteSize)

  override fun startUp() {
    running.set(true)
    thread.start()
  }

  override fun shutDown() {
    if (running.compareAndSet(true, false)) {
      thread.join(1000)
    }
  }

  override fun run() {
    while (running.get()) {
      runOnce()
    }
  }

  fun runOnce() {
    val event = events.poll()
    if (event != null) {
      val eventJson = eventAdapter.toJson(event)
      if (!batch.canFit(eventJson)) {
        client.flush(batch.events)
        batch.reset()
      }

      batch.append(eventJson, event.timestamp)
    } else {
      // Wait a little bit before checking for more events or for flush expiration
      try {
        Thread.sleep(100)
      } catch (e: InterruptedException) {
        running.set(false)
      }
    }

    // Flush if we are past the interval
    if (batch.isNotEmpty() && client.needsFlush()) {
      client.flush(batch.events)
      batch.reset()
    }
  }

  companion object {
    // See http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
    const val DEFAULT_MAX_BATCH_BYTES_SIZE = 1048576
    const val LOG_ENTRY_BYTE_OVERHEAD = 26
  }

  private class LogClient(
    private val config: CloudwatchLogConfig,
    private val logs: AWSLogs,
    private val logGroupName: String,
    private val logStreamName: String,
    private val clock: Clock
  ) {
    private var lastFlushTime = clock.millis()

    private var nextSequenceToken = ""

    fun needsFlush() = clock.millis() > lastFlushTime + config.max_flush_delay.toMillis()

    fun flush(events: List<InputLogEvent>) {
      try {
        // TODO(mmihic): Maybe consider allowing configuration of these flags (although unclear in
        // what environment it would be necessary to do so)
        retry(5, ExponentialBackoff(Duration.ofMillis(20), Duration.ofMillis(1000))) {
          try {
            val orderedEvents = events.sortedBy { it.timestamp }
            val request = PutLogEventsRequest(logGroupName, logStreamName, orderedEvents)
                .withSequenceToken(nextSequenceToken)
            val result = logs.putLogEvents(request)
            nextSequenceToken = result.nextSequenceToken
          } catch (e: InvalidSequenceTokenException) {
            nextSequenceToken = e.expectedSequenceToken
            throw e
          }
        }
      } catch (e: Exception) {
        // NB(mmihic): Ideally we'd have metrics for this since we can't log
      }


      lastFlushTime = clock.millis()
    }
  }

  private class EventBatch(
    private val maxItems: Int,
    private val maxByteSize: Int
  ) {
    private var _events = mutableListOf<InputLogEvent>()
    private var byteSize = 0

    val events: List<InputLogEvent> get() = _events.toList()

    /** Resets the batch */
    fun reset() {
      _events.clear()
      byteSize = 0
    }

    /** @return true if the batch is not empty */
    fun isNotEmpty() = events.isNotEmpty()

    /** @return if the batch can hold an event of a given size */
    fun canFit(eventJson: String) =
        events.size < maxItems && byteSize + eventByteSize(eventJson) < maxByteSize

    /** Appends the event to the batch */
    fun append(eventJson: String, timestamp: Long) {
      _events.add(InputLogEvent().withMessage(eventJson).withTimestamp(timestamp))
      byteSize += eventByteSize(eventJson)
    }

    private fun eventByteSize(eventJson: String) =
        eventJson.toByteArray().size + LOG_ENTRY_BYTE_OVERHEAD
  }
}

