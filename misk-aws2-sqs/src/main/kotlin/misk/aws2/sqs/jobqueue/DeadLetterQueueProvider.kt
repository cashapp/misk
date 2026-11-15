package misk.aws2.sqs.jobqueue

import com.google.inject.ImplementedBy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.jobqueue.QueueName

/**
 * Interface for a provider of dead-letter queue names.
 *
 * Returns the name of the appropriate dead-letter [QueueName] for a given [QueueName].
 */
@ImplementedBy(DefaultDeadLetterQueueProvider::class)
interface DeadLetterQueueProvider {
  fun deadLetterQueueFor(queue: QueueName): QueueName
}

/** Default provider of dead-letter [QueueName]. Returns the name of the main queue suffixed with "_dlq". */
@Singleton
class DefaultDeadLetterQueueProvider @Inject constructor() : DeadLetterQueueProvider {
  override fun deadLetterQueueFor(queue: QueueName): QueueName = queue.deadLetterQueue
}

/**
 * Provider of dead-letter [QueueName] that always returns the same value, no matter the supplied queue.
 *
 * For apps with queues that share a single dead-letter queue.
 */
class StaticDeadLetterQueueProvider(queue: String) : DeadLetterQueueProvider {
  private val dlq = QueueName(queue)

  override fun deadLetterQueueFor(queue: QueueName): QueueName = dlq
}

internal const val deadLetterQueueSuffix = "_dlq"

internal val QueueName.isDeadLetterQueue
  get() = value.endsWith(deadLetterQueueSuffix)
internal val QueueName.deadLetterQueue
  get() = if (isDeadLetterQueue) this else QueueName(parentQueue.value + deadLetterQueueSuffix)

internal const val retryQueueSuffix = "_retryq"
val QueueName.isRetryQueue
  get() = value.endsWith(retryQueueSuffix)
val QueueName.retryQueue
  get() = if (isRetryQueue) this else QueueName(parentQueue.value + retryQueueSuffix)

val QueueName.parentQueue
  get() =
    when {
      isDeadLetterQueue -> QueueName(value.removeSuffix(deadLetterQueueSuffix))
      isRetryQueue -> QueueName(value.removeSuffix(retryQueueSuffix))
      else -> this
    }
