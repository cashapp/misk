package misk.vitess

import com.google.common.base.Supplier
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import misk.jdbc.DataSourceService
import misk.jdbc.mapNotNull
import misk.logging.getLogger
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTimeoutException
import java.sql.SQLTransientException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ShardsLoader {
  private val logger = getLogger<ShardsLoader>()

  private val vitessCache: LoadingCache<DataSourceService, Set<Shard>> = CacheBuilder.newBuilder()
    .refreshAfterWrite(4, TimeUnit.MINUTES)
    .expireAfterWrite(6, TimeUnit.MINUTES)
    .build(object : CacheLoader<DataSourceService, Set<Shard>>() {
      override fun load(dataSourceService: DataSourceService): Set<Shard> {
        return loadVitessShards(dataSourceService)
      }
    })

  fun shards(dataSourceService: DataSourceService): Supplier<Set<Shard>> {
    return if (!dataSourceService.config().type.isVitess) {
      Supplier { Shard.SINGLE_SHARD_SET }
    } else {
      Supplier { vitessCache.get(dataSourceService) }
    }
  }

  private fun loadVitessShards(dataSourceService: DataSourceService): Set<Shard> {
    val maxRetries = 3
    var lastException: Exception? = null
    val retryableErrorMessages = listOf(
      "timeout",
      "context canceled",
      "connection closed"
    )

    repeat(maxRetries) { attempt ->
      try {
        return dataSourceService.dataSource.connection.use { connection ->
          connection.createStatement().use { statement ->
            // Set aggressive 5-second timeout for fast failure
            statement.queryTimeout = 5

            val resultSet = statement.executeQuery("SHOW VITESS_SHARDS")

            val shards = resultSet.mapNotNull { rs ->
              try {
                val shardName = rs.getString(1)
                Shard.parse(shardName)
              } catch (e: Exception) {
                logger.warn(e) { "Failed to parse shard from result: ${rs.getString(1)}" }
                null
              }
            }.toSet()

            if (shards.isEmpty()) {
              throw SQLRecoverableException("SHOW VITESS_SHARDS returned empty result set")
            }

            shards
          }
        }

      } catch (e: Exception) {
        lastException = e

        val isRetryableException =
            e.cause is SQLTimeoutException || e is SQLTimeoutException ||
            e.cause is SQLRecoverableException || e is SQLRecoverableException ||
            e.cause is SQLTransientException || e is SQLTransientException

        val isRetryableMessage = retryableErrorMessages.any { errorMessage ->
          e.message?.contains(errorMessage, ignoreCase = true) == true
        }

        val isRetryable = isRetryableException || isRetryableMessage

        logger.warn(e) {
          "Failed to load Vitess shards on attempt ${attempt + 1}/$maxRetries" +
            if (isRetryable) " (retryable exception detected)" else ""
        }

        // Only retry on timeouts/connectivity issues, and not on the last attempt
        if (isRetryable && attempt < maxRetries - 1) {
          val backoffMs = calculateBackoff(attempt)
          logger.info { "Retrying shard loading in ${backoffMs}ms..." }

          try {
            Thread.sleep(backoffMs)
          } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error { "Shard loading interrupted" }
            throw interrupted
          }
        } else {
          throw e // immediately rethrow without retrying
        }
      }
    }

    // All retries exhausted -- shouldn't reach here
    throw SQLException(
      "Failed to load Vitess shards after $maxRetries attempts. " +
        "Check Vitess connectivity and query timeout settings.",
      lastException
    )
  }

  private fun calculateBackoff(attempt: Int): Long {
    val baseDelayMs = 100L // Start with 100ms
    val maxDelayMs = 1000L // Cap at 1 second
    val exponentialDelay = baseDelayMs * (1L shl attempt) // 100ms, 200s, 400s, 800ms...
    val jitter = Random.nextLong(0, 30) // Add up to 30ms jitter
    return minOf(exponentialDelay + jitter, maxDelayMs)
  }
}
