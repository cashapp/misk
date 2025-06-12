package misk.redis.lettuce.cluster.testing

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import misk.redis.lettuce.cluster.ClusterInfo
import misk.redis.lettuce.cluster.ClusterNode
import misk.redis.lettuce.cluster.toClusterInfo
import misk.redis.lettuce.cluster.toClusterNodes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Extension function for [StatefulRedisClusterConnection] that waits for a Redis cluster to be ready.
 *
 * A Redis cluster is considered ready when:
 * 1. All primary nodes are connected and not in a failing state
 * 2. All primary nodes have slots assigned
 * 3. The cluster state is OK
 * 4. The expected number of slots are assigned and in OK state
 *
 * The function polls the cluster status at regular intervals until all conditions are met or
 * the timeout is reached.
 *
 * @param expectedSlotCount The number of slots that should be assigned in the cluster (default: 16384)
 * @param timeout Maximum time to wait for the cluster to be ready (default: 10 seconds)
 * @param pollInterval Time between status checks (default: 500 milliseconds)
 * @return A [CompletableFuture] that completes with the number of OK slots when the cluster is ready
 * @throws java.util.concurrent.TimeoutException if the timeout is reached before the cluster is ready
 */
fun StatefulRedisClusterConnection<*, *>.waitForRedisClusterReady(
  expectedSlotCount: Int = 16384,
  timeout: Duration = 10.seconds,
  pollInterval: Duration = 500.milliseconds
): CompletableFuture<Int> =
  retryableFuture(
    RetryConfig.Builder(
      upTo = Int.MAX_VALUE,
      withBackoff = FlatBackoff(pollInterval.toJavaDuration()),
    ).build(),
  ) {
    // Check the "master" nodes to verify that they are all connected and have slots assigned
    async().clusterNodes().toClusterNodes()
      .thenApply { result ->
        check(
          result
            .filter { it.role == ClusterNode.Role.Primary }
            .all { clusterNode ->
              clusterNode.isConnected
                && !clusterNode.isFailing
                && clusterNode.hasSlots
            },
        )
      }.thenCompose {
        // Check and return the assigned slots
        async().clusterInfo().toClusterInfo()
          .thenApply { clusterInfo: ClusterInfo ->
            check(clusterInfo.state == ClusterInfo.State.Ok)
            check(clusterInfo.slotsAssigned == expectedSlotCount)
            check(clusterInfo.slotsOk == expectedSlotCount)
            clusterInfo.slotsOk
          }
      }
      .toCompletableFuture()
  }.orTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
