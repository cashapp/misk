package misk.jobqueue

/**  A [JobConsumer] allows applications to subscribe to receive incoming jobs */
interface JobConsumer {
  /**
   * Registers a handler to receive messages. Once registered, the consumer will immediately
   * begin receiving messages from the underyling job queue and dispatch them to the provided
   * handler. A service may only have one subscription outstanding per queue
   *
   * @return a [Subscription] that can be closed to stop receiving jobs
   */
  fun subscribe(queueName: QueueName, handler: JobHandler): Subscription

  interface Subscription {
    /**
     * Closes the  subscription and stops receiving jobs from the underlying queue. Jobs
     * that are already in flight will continue to be dispatched to the handler, but
     * no new messages will be retrieved
     */
    fun close()
  }
}

inline fun JobConsumer.subscribe(queueName: QueueName, crossinline handler: (Job) -> Unit) =
    subscribe(queueName, object : JobHandler {
      override fun handleJob(job: Job) {
        handler(job)
      }
    })