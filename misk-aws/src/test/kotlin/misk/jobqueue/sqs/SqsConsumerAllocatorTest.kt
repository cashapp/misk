package misk.jobqueue.sqs

import misk.MiskTestingServiceModule
import misk.clustering.fake.lease.FakeLeaseManager
import misk.clustering.fake.lease.FakeLeaseModule
import misk.feature.testing.FakeFeatureFlags
import misk.feature.testing.FakeFeatureFlagsModule
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.AwsSqsJobReceiverPolicy.BALANCED_MAX
import misk.jobqueue.sqs.AwsSqsJobReceiverPolicy.ONE_FLAG_ONLY
import misk.jobqueue.sqs.SqsJobConsumer.Companion.CONSUMERS_PER_QUEUE
import misk.jobqueue.sqs.SqsJobConsumer.Companion.POD_CONSUMERS_PER_QUEUE
import misk.jobqueue.sqs.SqsJobConsumer.Companion.POD_MAX_JOBQUEUE_CONSUMERS
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertEquals

@MiskTest(startService = false)
class SqsConsumerAllocatorTest {

  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(FakeLeaseModule())
      install(FakeFeatureFlagsModule())
    }
  }

  @Inject private lateinit var featureFlags: FakeFeatureFlags
  @Inject private lateinit var leaseManager: FakeLeaseManager
  @Inject private lateinit var sqsConsumerAllocator: SqsConsumerAllocator

  private val queueName = QueueName("example-queue")

  @Nested
  inner class OneFlagPolicyTest {

    @Test
    fun consumersPerQueue() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_CONSUMERS_PER_QUEUE, -1)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, 0)

      assertEquals(15, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, ONE_FLAG_ONLY))

      // Take some leases
      (1..10).forEach {
        leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it))
      }
      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, ONE_FLAG_ONLY))
    }

    @Test
    fun consumersPerPod() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_CONSUMERS_PER_QUEUE, 5)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, 0)
      // Take some leases.
      (1..10).forEach {
        leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it))
      }
      // Leases don't matter.. we take all available according to the flag.
      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, ONE_FLAG_ONLY))
    }
  }

  @Nested
  inner class BoundedMaxTest {

    @Test
    fun `hit global ceiling`() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, 10)
      // Take some leases.
      (1..10).forEach {
        leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it))
      }
      // Even though this pod can take up to 10 consumers, only 5 are available.
      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, BALANCED_MAX))
    }

    @Test
    fun `hit per pod ceiling`() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, 5)
      // Take some leases.
      (1..2).forEach {
        leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it))
      }
      // Even though there are 13 consumers available globally, this pod is capped at 5.
      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, BALANCED_MAX))
    }

    @Test
    fun `noop on negative pod ceiling`() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, -1)

      // No pod max specified, do nothing.
      assertEquals(0, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, BALANCED_MAX))
    }
  }
}
