package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import misk.jobqueue.QueueName
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class QueueUrlMapping @Inject internal constructor(private val sqs: AmazonSQS) {
  private val mapping = ConcurrentHashMap<QueueName, String>()

  operator fun get(q: QueueName): String {
    return mapping.computeIfAbsent(q) {
      sqs.getQueueUrl(q.value).queueUrl
    }
  }
}

internal val QueueName.isDeadLetterQueue get() = value.endsWith("_dlq")
internal val QueueName.deadLetterQueue
  get() = if (isDeadLetterQueue) this else QueueName(this.value + "_dlq")