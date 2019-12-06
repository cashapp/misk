package misk.jobqueue

/**  A [JobConsumer] allows applications to subscribe to receive incoming jobs */
interface JobConsumer {
  /**
   * Associates a message handler to a prioritized list of queues. Once registered, the consumer
   * will immediately begin receiving messages from the underlying job queues and dispatch them to
   * the provided handler.
   *
   * If more than one queue name is provided, these will be treated as a list of prioritized queues
   * (in priority order, first queue is top priority). Messages will be pulled from the highest
   * priority queue first. If that queue doesn't return any messages, we keep trying the next queue
   * in the list, until we reach either a queue that returns a message or the end of the list, and
   * then start at the beginning again.
   *
   * Note that if processing on the higher priority queues gets behind, the lower priority queues
   * will be starved.
   */
  fun subscribe(queueNames: List<QueueName>, handler: JobHandler)
}

inline fun JobConsumer.subscribe(queueName: QueueName, crossinline handler: (Job) -> Unit) =
    subscribe(listOf(queueName), object : JobHandler {
      override fun handleJob(job: Job) {
        handler(job)
      }
    })
