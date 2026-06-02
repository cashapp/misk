package misk.jobqueue.v2

import misk.jobqueue.QueueName

/** A [JobConsumer] allows applications to subscribe to receive incoming jobs */
interface JobConsumer {
  /**
   * Registers a handler to receive messages. Once registered, the consumer will immediately begin receiving messages
   * from the underlying job queue and dispatch them to the provided handler. A service may only have one subscription
   * outstanding per queue
   */
  fun subscribe(queueName: QueueName, handler: JobHandler)

  fun unsubscribe(queueName: QueueName)
}
