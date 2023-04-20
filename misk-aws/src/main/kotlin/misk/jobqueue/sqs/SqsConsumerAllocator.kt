package misk.jobqueue.sqs

import com.google.common.annotations.VisibleForTesting
import misk.feature.FeatureFlags
import misk.jobqueue.QueueName
import wisp.lease.LeaseManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uses a [LeaseManager] and [FeatureFlags] to calculate the number of sqs consumers a pods should
 * have. This computation is based off of the [AwsSqsJobReceiverPolicy] specification.
 */
@Singleton
class SqsConsumerAllocator @Inject constructor(
  private val config: AwsSqsJobQueueConfig,
  private val leaseManager: LeaseManager,
  private val featureFlags: FeatureFlags,
) {
  fun computeSqsConsumersForPod(
    queueName: QueueName,
    receiverPolicy: AwsSqsJobReceiverPolicy
  ): Int {
    return when (receiverPolicy) {
      AwsSqsJobReceiverPolicy.ONE_FLAG_ONLY -> oneFlagPriorityReceivers(queueName)
      AwsSqsJobReceiverPolicy.BALANCED_MAX -> balancedMaxReceivers(queueName)
    }
  }

  private fun oneFlagPriorityReceivers(queueName: QueueName): Int {
    val numReceiversPerPodForQueue = receiversPerPodForQueue(queueName)
    val shouldUsePerPodConfig = numReceiversPerPodForQueue >= 0
    if (shouldUsePerPodConfig) {
      return numReceiversPerPodForQueue
    }
    val count = (1..receiversForQueue(queueName)).count { maybeAcquireConsumerLease(queueName, it) }
    return count

  }

  private fun balancedMaxReceivers(queueName: QueueName): Int {
    // Read the max from the per pod flag.
    val maxPerPod = podMaxJobQueueConsumers(queueName)
    // Read the global max from the per queue flag.
    val maxGlobal = receiversForQueue(queueName)

    var result = 0
    for (candidate in 1..maxGlobal) {
      // Don't exceed the per pod max.
      if (result >= maxPerPod) break

      // Use the lease to enforce global max.
      if (maybeAcquireConsumerLease(queueName, candidate)) result += 1
    }
    return result
  }

  /** Returns true if the lease was acquired false otherwise. */
  private fun maybeAcquireConsumerLease(queueName: QueueName, candidate: Int): Boolean {
    if (config.ignore_leases) {
      return false
    }
    val lease = leaseManager.requestLease(leaseName(queueName, candidate))
    if (lease.checkHeld()) return true

    return lease.acquire()
  }

  private fun receiversPerPodForQueue(queueName: QueueName): Int {
    return featureFlags.getInt(SqsJobConsumer.POD_CONSUMERS_PER_QUEUE, queueName.value)
  }

  /** Returns a ceiling on # of consumers a pod should have. */
  private fun podMaxJobQueueConsumers(queueName: QueueName,): Int {
    return featureFlags.getInt(SqsJobConsumer.POD_MAX_JOBQUEUE_CONSUMERS, queueName.value).coerceAtLeast(0)
  }
  private fun receiversForQueue(queueName: QueueName,): Int {
    return featureFlags.getInt(SqsJobConsumer.CONSUMERS_PER_QUEUE, queueName.value)
  }

  companion object {
    @VisibleForTesting
    fun leaseName(queueName: QueueName, candidate: Int) =
      "sqs-job-consumer-${queueName.value}-$candidate"
  }

}
