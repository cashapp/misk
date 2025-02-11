package misk.aws2.sqs.jobqueue

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Inject
import com.google.inject.Singleton
import misk.jobqueue.QueueName
import misk.jobqueue.v2.JobHandler

@Singleton
class SubscriptionService @Inject constructor(
  private val consumer: SqsJobConsumer,
  private val handlers: Map<QueueName, JobHandler>,
  private val subscriptions: Map<QueueName, Subscription>,
): AbstractIdleService() {
  override fun startUp() {
    handlers.forEach { (queueName, handler) ->
      val subscription = subscriptions[queueName]!!
      consumer.subscribe(queueName, handler, subscription.parallelism, subscription.concurrency, subscription.channelCapacity)
    }
  }

  override fun shutDown() {
  }
}
