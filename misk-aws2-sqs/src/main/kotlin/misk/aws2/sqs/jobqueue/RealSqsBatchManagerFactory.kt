package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Singleton
import misk.aws2.sqs.jobqueue.config.SqsConfig
import misk.logging.getLogger
import software.amazon.awssdk.services.sqs.batchmanager.BatchOverrideConfiguration
import software.amazon.awssdk.services.sqs.batchmanager.SqsAsyncBatchManager
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Singleton
class RealSqsBatchManagerFactory(
  private val sqsClientFactory: SqsClientFactory,
  sqsConfig: SqsConfig,
) : SqsBatchManagerFactory, AbstractIdleService() {
  private val sendRequestFrequency: Duration = Duration.ofMillis(sqsConfig.buffered_batch_flush_frequency_ms)
  private val batchManagers = ConcurrentHashMap<String, SqsAsyncBatchManager>()
  private val scheduledExecutors = ConcurrentHashMap<String, ScheduledExecutorService>()

  override fun get(region: String): SqsAsyncBatchManager {
    return batchManagers.computeIfAbsent(region) {
      val client = sqsClientFactory.get(region)
      val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "sqs-async-batch-manager-$region").apply { isDaemon = true }
      }
      try {
        val batchManager = SqsAsyncBatchManager.builder()
          .client(client)
          .scheduledExecutor(executor)
          .overrideConfiguration(
            BatchOverrideConfiguration.builder()
              .sendRequestFrequency(sendRequestFrequency)
              .build()
          )
          .build()
        scheduledExecutors[region] = executor
        batchManager
      } catch (e: Exception) {
        executor.shutdown()
        throw e
      }
    }
  }

  override fun startUp() {
    // Batch managers are created lazily on first use
  }

  override fun shutDown() {
    logger.info { "Shutting down SqsAsyncBatchManager" }
    batchManagers.forEach { (region, batchManager) ->
      try {
        batchManager.close()
      } catch (e: Exception) {
        logger.error(e) { "Error closing SqsAsyncBatchManager for region $region" }
      }
    }
    scheduledExecutors.forEach { (region, executor) ->
      try {
        executor.shutdown()
      } catch (e: Exception) {
        logger.error(e) { "Error shutting down scheduled executor for region $region" }
      }
    }
    batchManagers.clear()
    scheduledExecutors.clear()
  }

  companion object {
    private val logger = getLogger<RealSqsBatchManagerFactory>()
  }
}
