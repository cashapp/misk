package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Provides
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.TimeUnit
import misk.MiskTestingServiceModule
import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests that [AwsSqsClientService] ensures proper shutdown ordering:
 * Services that depend on it must terminate before the SQS clients are shut down.
 */
@MiskTest(startService = false)
class AwsSqsClientServiceTest {
  @MiskTestModule
  private val module = TestModule()

  @Inject private lateinit var serviceManager: ServiceManager
  @Inject private lateinit var shutdownLog: ShutdownLog

  @Test
  fun `dependent service shuts down before AwsSqsClientService`() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    serviceManager.stopAsync()
    serviceManager.awaitStopped(30, TimeUnit.SECONDS)

    // Verify shutdown order: DependentService should stop before AwsSqsClientService
    assertThat(shutdownLog.events).containsExactly(
      "DependentService.shutDown",
      "AwsSqsClientService.shutDown",
    )
  }

  @Test
  fun `SQS client is available when dependent service shuts down`() {
    serviceManager.startAsync()
    serviceManager.awaitHealthy()

    serviceManager.stopAsync()
    serviceManager.awaitStopped(30, TimeUnit.SECONDS)

    // The dependent service should have been able to use the SQS client during shutdown
    assertThat(shutdownLog.sqsClientWasAvailable).isTrue()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(ServiceModule<AwsSqsClientService>())
      install(
        ServiceModule<DependentService>()
          .dependsOn<ReadyService>()
          .dependsOn<AwsSqsClientService>()
      )
    }

    @Provides
    @Singleton
    fun provideShutdownLog(): ShutdownLog = ShutdownLog()

    @Provides
    @Singleton
    fun provideSqsClient(log: ShutdownLog): AmazonSQS = log.trackingSqsClient

    @Provides
    @Singleton
    @ForSqsReceiving
    fun provideSqsReceivingClient(log: ShutdownLog): AmazonSQS = log.trackingSqsClient

    @Provides
    @Singleton
    fun provideAwsSqsClientService(
      sqsClient: AmazonSQS,
      @ForSqsReceiving sqsReceivingClient: AmazonSQS,
      log: ShutdownLog,
    ): AwsSqsClientService = object : AwsSqsClientService(sqsClient, sqsReceivingClient, emptyMap(), emptyMap()) {
      init {
        withShutdownClients(false)
      }
      override fun shutDown() {
        super.shutDown()
        log.events.add("AwsSqsClientService.shutDown")
      }
    }
  }

  /** Tracks shutdown events for assertions. */
  class ShutdownLog {
    val events = mutableListOf<String>()
    var sqsClientWasAvailable = false
    val trackingSqsClient = TrackingSqsClient { sqsClientWasAvailable = !it }
  }

  /** A service that depends on AwsSqsClientService and checks SQS availability during shutdown. */
  @Singleton
  class DependentService @Inject constructor(
    private val sqsClient: AmazonSQS,
    private val log: ShutdownLog,
  ) : AbstractIdleService() {
    override fun startUp() {}

    override fun shutDown() {
      // Check if SQS client is still available (not shut down yet)
      log.sqsClientWasAvailable = !(sqsClient as TrackingSqsClient).isShutdown
      log.events.add("DependentService.shutDown")
    }
  }

  /** A minimal SQS client that tracks shutdown state. */
  class TrackingSqsClient(private val onShutdown: (Boolean) -> Unit) : AmazonSQS {
    var isShutdown = false
      private set

    override fun shutdown() {
      isShutdown = true
      onShutdown(true)
    }

    // Minimal stub implementations
    override fun setEndpoint(endpoint: String?) {}
    override fun setRegion(region: com.amazonaws.regions.Region?) {}
    override fun addPermission(request: com.amazonaws.services.sqs.model.AddPermissionRequest?) = throw UnsupportedOperationException()
    override fun addPermission(queueUrl: String?, label: String?, aWSAccountIds: MutableList<String>?, actions: MutableList<String>?) = throw UnsupportedOperationException()
    override fun changeMessageVisibility(request: com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest?) = throw UnsupportedOperationException()
    override fun changeMessageVisibility(queueUrl: String?, receiptHandle: String?, visibilityTimeout: Int?) = throw UnsupportedOperationException()
    override fun changeMessageVisibilityBatch(request: com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequest?) = throw UnsupportedOperationException()
    override fun changeMessageVisibilityBatch(queueUrl: String?, entries: MutableList<com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry>?) = throw UnsupportedOperationException()
    override fun createQueue(request: com.amazonaws.services.sqs.model.CreateQueueRequest?) = throw UnsupportedOperationException()
    override fun createQueue(queueName: String?) = throw UnsupportedOperationException()
    override fun deleteMessage(request: com.amazonaws.services.sqs.model.DeleteMessageRequest?) = throw UnsupportedOperationException()
    override fun deleteMessage(queueUrl: String?, receiptHandle: String?) = throw UnsupportedOperationException()
    override fun deleteMessageBatch(request: com.amazonaws.services.sqs.model.DeleteMessageBatchRequest?) = throw UnsupportedOperationException()
    override fun deleteMessageBatch(queueUrl: String?, entries: MutableList<com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry>?) = throw UnsupportedOperationException()
    override fun deleteQueue(request: com.amazonaws.services.sqs.model.DeleteQueueRequest?) = throw UnsupportedOperationException()
    override fun deleteQueue(queueUrl: String?) = throw UnsupportedOperationException()
    override fun getQueueAttributes(request: com.amazonaws.services.sqs.model.GetQueueAttributesRequest?) = throw UnsupportedOperationException()
    override fun getQueueAttributes(queueUrl: String?, attributeNames: MutableList<String>?) = throw UnsupportedOperationException()
    override fun getQueueUrl(request: com.amazonaws.services.sqs.model.GetQueueUrlRequest?) = throw UnsupportedOperationException()
    override fun getQueueUrl(queueName: String?) = throw UnsupportedOperationException()
    override fun listDeadLetterSourceQueues(request: com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesRequest?) = throw UnsupportedOperationException()
    override fun listQueues(request: com.amazonaws.services.sqs.model.ListQueuesRequest?) = throw UnsupportedOperationException()
    override fun listQueues() = throw UnsupportedOperationException()
    override fun listQueues(queueNamePrefix: String?) = throw UnsupportedOperationException()
    override fun listQueueTags(request: com.amazonaws.services.sqs.model.ListQueueTagsRequest?) = throw UnsupportedOperationException()
    override fun listQueueTags(queueUrl: String?) = throw UnsupportedOperationException()
    override fun purgeQueue(request: com.amazonaws.services.sqs.model.PurgeQueueRequest?) = throw UnsupportedOperationException()
    override fun receiveMessage(request: com.amazonaws.services.sqs.model.ReceiveMessageRequest?) = throw UnsupportedOperationException()
    override fun receiveMessage(queueUrl: String?) = throw UnsupportedOperationException()
    override fun removePermission(request: com.amazonaws.services.sqs.model.RemovePermissionRequest?) = throw UnsupportedOperationException()
    override fun removePermission(queueUrl: String?, label: String?) = throw UnsupportedOperationException()
    override fun sendMessage(request: com.amazonaws.services.sqs.model.SendMessageRequest?) = throw UnsupportedOperationException()
    override fun sendMessage(queueUrl: String?, messageBody: String?) = throw UnsupportedOperationException()
    override fun sendMessageBatch(request: com.amazonaws.services.sqs.model.SendMessageBatchRequest?) = throw UnsupportedOperationException()
    override fun sendMessageBatch(queueUrl: String?, entries: MutableList<com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry>?) = throw UnsupportedOperationException()
    override fun setQueueAttributes(request: com.amazonaws.services.sqs.model.SetQueueAttributesRequest?) = throw UnsupportedOperationException()
    override fun setQueueAttributes(queueUrl: String?, attributes: MutableMap<String, String>?) = throw UnsupportedOperationException()
    override fun tagQueue(request: com.amazonaws.services.sqs.model.TagQueueRequest?) = throw UnsupportedOperationException()
    override fun tagQueue(queueUrl: String?, tags: MutableMap<String, String>?) = throw UnsupportedOperationException()
    override fun untagQueue(request: com.amazonaws.services.sqs.model.UntagQueueRequest?) = throw UnsupportedOperationException()
    override fun untagQueue(queueUrl: String?, tagKeys: MutableList<String>?) = throw UnsupportedOperationException()
    override fun getCachedResponseMetadata(request: com.amazonaws.AmazonWebServiceRequest?) = throw UnsupportedOperationException()
    override fun cancelMessageMoveTask(request: com.amazonaws.services.sqs.model.CancelMessageMoveTaskRequest?) = throw UnsupportedOperationException()
    override fun listMessageMoveTasks(request: com.amazonaws.services.sqs.model.ListMessageMoveTasksRequest?) = throw UnsupportedOperationException()
    override fun startMessageMoveTask(request: com.amazonaws.services.sqs.model.StartMessageMoveTaskRequest?) = throw UnsupportedOperationException()
  }
}
