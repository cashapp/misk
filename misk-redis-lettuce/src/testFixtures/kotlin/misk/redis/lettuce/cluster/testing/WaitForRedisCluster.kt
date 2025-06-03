package misk.redis.lettuce.cluster.testing

import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.models.slots.ClusterSlotsParser
import misk.backoff.FlatBackoff
import misk.backoff.RetryConfig
import misk.backoff.retryableFuture
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
    async().clusterSlots()
      .thenApply { ClusterSlotsParser.parse(it) }
      .thenApply { ranges -> ranges.sumOf { it.to - it.from + 1 } }
      .thenCompose { slots ->
        check(slots >= expectedSlotCount) { "Waiting for cluster... current slots: $slots" }
        CompletableFuture.completedFuture(slots)
      }.toCompletableFuture()
  }.orTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
