package misk.jobqueue.sqs

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
@Deprecated(
  message = "AWS SDK v1 SQS jobqueue is deprecated. Use " +
    "misk.aws2.sqs.jobqueue.DeadLetterQueueProvider with the AWS SDK v2 SQS jobqueue instead."
)
interface DeadLetterQueueProvider {
  fun deadLetterQueueFor(queue: QueueName): QueueName
}

/** Default provider of dead-letter [QueueName]. Returns the name of the main queue suffixed with "_dlq". */
@Singleton
@Deprecated(
  message = "AWS SDK v1 SQS jobqueue is deprecated. Use " +
    "misk.aws2.sqs.jobqueue.DefaultDeadLetterQueueProvider with the AWS SDK v2 SQS jobqueue instead."
)
class DefaultDeadLetterQueueProvider @Inject constructor() : DeadLetterQueueProvider {
  override fun deadLetterQueueFor(queue: QueueName): QueueName = queue.deadLetterQueue
}

/**
 * Provider of dead-letter [QueueName] that always returns the same value, no matter the supplied queue.
 *
 * For apps with queues that share a single dead-letter queue.
 */
@Deprecated(
  message = "AWS SDK v1 SQS jobqueue is deprecated. Use " +
    "misk.aws2.sqs.jobqueue.StaticDeadLetterQueueProvider with the AWS SDK v2 SQS jobqueue instead."
)
class StaticDeadLetterQueueProvider(queue: String) : DeadLetterQueueProvider {
  private val dlq = QueueName(queue)

  override fun deadLetterQueueFor(queue: QueueName): QueueName = dlq
}
