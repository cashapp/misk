package misk.aws2.sqs.jobqueue.coordinated

import com.google.common.util.concurrent.AbstractIdleService
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import misk.cloud.aws.AwsRegion
import misk.logging.getLogger
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.SqsClientBuilder
import software.amazon.awssdk.services.sqs.batchmanager.BatchOverrideConfiguration
import software.amazon.awssdk.services.sqs.batchmanager.SqsAsyncBatchManager

internal interface SqsClientFactory {
  fun getForSending(region: AwsRegion): SqsClient
  fun getForReceiving(region: AwsRegion): SqsClient
  fun getBatchManager(region: AwsRegion): SqsAsyncBatchManager
}

internal class RealSqsClientFactory(
  private val config: AwsSqsJobQueueConfig,
  private val credentialsProvider: AwsCredentialsProvider,
  private val configureSyncClient: (SqsClientBuilder) -> Unit,
  private val configureAsyncClient: (SqsAsyncClientBuilder) -> Unit,
) : SqsClientFactory, AbstractIdleService() {
  private val sendingClients = ConcurrentHashMap<AwsRegion, SqsClient>()
  private val receivingClients = ConcurrentHashMap<AwsRegion, SqsClient>()
  private val asyncSendingClients = ConcurrentHashMap<AwsRegion, SqsAsyncClient>()
  private val batchManagers = ConcurrentHashMap<AwsRegion, SqsAsyncBatchManager>()
  private val scheduledExecutors = ConcurrentHashMap<AwsRegion, ScheduledExecutorService>()

  override fun getForSending(region: AwsRegion): SqsClient {
    return sendingClients.computeIfAbsent(region) {
      SqsClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region.name))
        .overrideConfiguration(sendingOverrideConfiguration())
        .also(configureSyncClient)
        .build()
    }
  }

  override fun getForReceiving(region: AwsRegion): SqsClient {
    return receivingClients.computeIfAbsent(region) {
      SqsClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region.name))
        .overrideConfiguration(receivingOverrideConfiguration())
        .also(configureSyncClient)
        .build()
    }
  }

  override fun getBatchManager(region: AwsRegion): SqsAsyncBatchManager {
    return batchManagers.computeIfAbsent(region) {
      val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
          Thread(runnable, "sqs-async-batch-manager-${region.name}").apply { isDaemon = true }
        }
      try {
        val batchManager =
          SqsAsyncBatchManager.builder()
            .client(getAsyncForSending(region))
            .scheduledExecutor(executor)
            .overrideConfiguration(
              BatchOverrideConfiguration.builder()
                .sendRequestFrequency(BUFFERED_SEND_FREQUENCY)
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

  override fun startUp() {}

  override fun shutDown() {
    batchManagers.forEach { (region, batchManager) ->
      try {
        batchManager.close()
      } catch (e: Exception) {
        log.error(e) { "error closing SQS batch manager for ${region.name}" }
      }
    }
    asyncSendingClients.forEach { (region, client) ->
      try {
        client.close()
      } catch (e: Exception) {
        log.error(e) { "error closing async SQS client for ${region.name}" }
      }
    }
    sendingClients.forEach { (region, client) ->
      try {
        client.close()
      } catch (e: Exception) {
        log.error(e) { "error closing sending SQS client for ${region.name}" }
      }
    }
    receivingClients.forEach { (region, client) ->
      try {
        client.close()
      } catch (e: Exception) {
        log.error(e) { "error closing receiving SQS client for ${region.name}" }
      }
    }
    scheduledExecutors.forEach { (region, executor) ->
      try {
        executor.shutdown()
      } catch (e: Exception) {
        log.error(e) { "error shutting down SQS batch manager executor for ${region.name}" }
      }
    }
  }

  private fun getAsyncForSending(region: AwsRegion): SqsAsyncClient {
    return asyncSendingClients.computeIfAbsent(region) {
      SqsAsyncClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region.name))
        .overrideConfiguration(sendingOverrideConfiguration())
        .also(configureAsyncClient)
        .build()
    }
  }

  private fun sendingOverrideConfiguration(): ClientOverrideConfiguration {
    return ClientOverrideConfiguration.builder()
      .apiCallAttemptTimeout(Duration.ofMillis(config.sqs_sending_socket_timeout_ms.toLong()))
      .apiCallTimeout(Duration.ofMillis(config.sqs_sending_request_timeout_ms.toLong()))
      .build()
  }

  private fun receivingOverrideConfiguration(): ClientOverrideConfiguration {
    return ClientOverrideConfiguration.builder()
      .apiCallAttemptTimeout(RECEIVING_ATTEMPT_TIMEOUT)
      .build()
  }

  companion object {
    private val log = getLogger<RealSqsClientFactory>()
    private val BUFFERED_SEND_FREQUENCY = Duration.ofMillis(200)
    private val RECEIVING_ATTEMPT_TIMEOUT = Duration.ofMillis(25_000)
  }
}
