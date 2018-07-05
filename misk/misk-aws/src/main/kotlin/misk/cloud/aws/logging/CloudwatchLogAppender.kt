package misk.cloud.aws.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import java.util.concurrent.BlockingQueue

internal class CloudwatchLogAppender internal constructor(
  private val eventQueue: BlockingQueue<CloudwatchLogEvent>
) : UnsynchronizedAppenderBase<ILoggingEvent>() {
  override fun append(event: ILoggingEvent) {
    eventQueue.offer(CloudwatchLogEvent(event))
  }
}