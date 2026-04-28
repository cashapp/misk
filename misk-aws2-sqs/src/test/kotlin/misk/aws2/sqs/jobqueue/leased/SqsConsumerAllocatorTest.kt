package misk.aws2.sqs.jobqueue.leased

import jakarta.inject.Inject
import kotlin.test.assertEquals
import misk.MiskTestingServiceModule
import misk.aws2.sqs.jobqueue.leased.AwsSqsJobReceiverPolicy.BALANCED_MAX
import misk.aws2.sqs.jobqueue.leased.AwsSqsJobReceiverPolicy.ONE_FLAG_ONLY
import misk.aws2.sqs.jobqueue.leased.SqsJobConsumer.Companion.CONSUMERS_PER_QUEUE
import misk.aws2.sqs.jobqueue.leased.SqsJobConsumer.Companion.POD_CONSUMERS_PER_QUEUE
import misk.aws2.sqs.jobqueue.leased.SqsJobConsumer.Companion.POD_MAX_JOBQUEUE_CONSUMERS
import misk.clustering.fake.lease.FakeLeaseManager
import misk.clustering.fake.lease.FakeLeaseModule
import misk.feature.testing.FakeFeatureFlags
import misk.feature.testing.FakeFeatureFlagsModule
import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class SqsConsumerAllocatorTest {

  @MiskTestModule
  val module =
    object : KAbstractModule() {
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

      (1..10).forEach { leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it)) }
      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, ONE_FLAG_ONLY))
    }

    @Test
    fun consumersPerPod() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_CONSUMERS_PER_QUEUE, 5)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, 0)

      (1..10).forEach { leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it)) }

      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, ONE_FLAG_ONLY))
    }
  }

  @Nested
  inner class BoundedMaxTest {

    @Test
    fun `hit global ceiling`() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, 10)

      (1..10).forEach { leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it)) }

      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, BALANCED_MAX))
    }

    @Test
    fun `hit per pod ceiling`() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, 5)

      (1..2).forEach { leaseManager.markLeaseHeldElsewhere(SqsConsumerAllocator.leaseName(queueName, it)) }

      assertEquals(5, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, BALANCED_MAX))
    }

    @Test
    fun `noop on negative pod ceiling`() {
      featureFlags.override(CONSUMERS_PER_QUEUE, 15)
      featureFlags.override(POD_MAX_JOBQUEUE_CONSUMERS, -1)

      assertEquals(0, sqsConsumerAllocator.computeSqsConsumersForPod(queueName, BALANCED_MAX))
    }
  }
}
