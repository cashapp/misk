package misk.cloud.aws.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.filter.ThresholdFilter
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.logs.AWSLogs
import com.amazonaws.services.logs.AWSLogsClientBuilder
import com.google.common.annotations.VisibleForTesting
import com.squareup.moshi.Moshi
import misk.environment.InstanceMetadata
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/** Enables logging for Cloudwatch Trail */
object CloudwatchTrail {
  /** Enable Cloudwatch Trail logging for the given app */
  fun enableLogging(
    appName: String,
    instanceMetadata: InstanceMetadata,
    config: CloudwatchLogConfig
  ) {
    val eventQueue = ArrayBlockingQueue<CloudwatchLogEvent>(config.event_buffer_size)
    registerAppender(config, eventQueue)

    val logs = buildLogsClient(instanceMetadata, config)
    val service = CloudwatchLogService(
        appName = appName,
        clock = Clock.systemUTC(),
        config = config,
        logs = logs,
        events = eventQueue,
        moshi = Moshi.Builder().build())
    service.startAsync()
  }

  @VisibleForTesting
  internal fun registerAppender(
    config: CloudwatchLogConfig,
    eventQueue: BlockingQueue<CloudwatchLogEvent>
  ) {
    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    val context = rootLogger.loggerContext
    context.reset()

    val filter = ThresholdFilter()
    filter.setLevel(config.filter_level.levelStr)
    filter.context = context
    filter.start()

    // TODO(mmihic): Enhance with tracing information
    val appender = CloudwatchLogAppender(eventQueue)
    appender.addFilter(filter)
    appender.context = context
    appender.start()

    rootLogger.addAppender(appender)
  }

  private fun buildLogsClient(
    instanceMetadata: InstanceMetadata,
    config: CloudwatchLogConfig
  ): AWSLogs {
    val builder = AWSLogsClientBuilder.standard()
    if (config.endpoint != null) {
      val endpoint = EndpointConfiguration(config.endpoint, instanceMetadata.region)
      builder.setEndpointConfiguration(endpoint)
      builder.credentials = AWSStaticCredentialsProvider(AnonymousAWSCredentials())
    } else {
      builder.region = instanceMetadata.region
    }

    return builder.build()
  }
}