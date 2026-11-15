package misk.redis2.metrics

import io.lettuce.core.metrics.CommandLatencyRecorder
import io.lettuce.core.protocol.ProtocolKeyword
import java.net.SocketAddress
import kotlin.time.Duration.Companion.nanoseconds
import misk.redis.lettuce.metrics.RedisClientMetrics

/**
 * A Lettuce command latency recorder that integrates with Misk metrics.
 *
 * This class implements Lettuce's [CommandLatencyRecorder] interface to capture Redis command latency metrics. It
 * records both first response time and total operation time for each Redis command, providing detailed performance
 * monitoring capabilities.
 *
 * Key features:
 * - Records command-specific latency metrics
 * - Tracks both first response and total completion times
 * - Includes network address information
 * - Groups metrics by replication group ID
 *
 * Metrics recorded:
 * - **First Response Time**: Time until the first response is received
 * - **Operation Time**: Total time until command completion
 */
internal class RedisClientMetricsCommandLatencyRecorder(
  private val replicationGroupId: String,
  private val clientMetrics: RedisClientMetrics,
) : CommandLatencyRecorder {

  override fun recordCommandLatency(
    local: SocketAddress,
    remote: SocketAddress,
    commandType: ProtocolKeyword?,
    firstResponseLatency: Long,
    completionLatency: Long,
  ) {
    with(clientMetrics) {
      recordFirstResponseTime(
        replicationGroupId = replicationGroupId,
        commandType = commandType.toString(),
        value = firstResponseLatency.nanoseconds,
      )

      recordOperationTime(
        replicationGroupId = replicationGroupId,
        commandType = commandType.toString(),
        value = completionLatency.nanoseconds,
      )
    }
  }
}
