package misk.aws2.sqs.jobqueue

/**
 * Controls runtime SQS consumption for a queue.
 *
 * A queue is the SQS queue registered for a job handler. A slot is one held unit of
 * consumption for that queue. Each held slot starts one SQS poller and [handlersPerSlot]
 * handler coroutines. Handler coroutines for all held slots share the queue's configured
 * handler parallelism. The slot map key is the stable identity used to reconcile held slots
 * across refreshes.
 */
interface SqsConsumptionController {
  val handlersPerSlot: Int

  suspend fun acquireSlots(): Map<String, SqsConsumerSlot>
}

interface SqsConsumerSlot {
  fun isHeld(): Boolean

  fun release()
}
