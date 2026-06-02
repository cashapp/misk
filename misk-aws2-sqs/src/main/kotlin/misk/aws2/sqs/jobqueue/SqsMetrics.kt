package misk.aws2.sqs.jobqueue

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.metrics.v2.Metrics

/**
 * SQS Jobqueue metrics.
 *
 * NB: we use the capitalized "QueueName" label to stay consistent with SQS' similarly named label. This lets us filter
 * for queues both client-side and on SQS with the same label.
 */
@Singleton
class SqsMetrics @Inject internal constructor(metrics: Metrics) {
  val jobsEnqueued =
    metrics.counter("jobs_enqueued_v2_total", "total # of jobs sent to a queueName", listOf("QueueName"))

  val jobsBatchEnqueued =
    metrics.counter("jobs_batch_enqueued_v2_total", "total # of jobs sent in batch to a queueName", listOf("QueueName"))

  val jobEnqueueFailures =
    metrics.counter("job_enqueue_failures_v2_total", "total # of jobs that failed to enqueue", listOf("QueueName"))

  val jobBatchEnqueueFailures =
    metrics.counter(
      "job_batch_enqueue_failures_v2_total",
      "total # of jobs that failed to enqueue in batch",
      listOf("QueueName"),
    )

  val jobsReceived =
    metrics.counter("jobs_received_v2_total", "total # of jobs received on a queueName", listOf("QueueName"))

  val handlerDispatchTime =
    metrics.histogram(
      "job_handler_duration_ms_v2",
      "duration of job handling runs for a given job queueName",
      listOf("QueueName"),
    )

  val jobsAcknowledged =
    metrics.counter("jobs_acknowledged_v2_total", "total # of jobs acknowledged by handlers", listOf("QueueName"))

  val jobsFailedToAcknowledge =
    metrics.counter(
      "jobs_failed_acknowledge_v2_total",
      "total # of jobs that we failed to acknowledge",
      listOf("QueueName"),
    )

  val handlerFailures =
    metrics.counter(
      "job_handler_failures_v2_total",
      "total # of jobs whose handlers threw an exception",
      listOf("QueueName"),
    )

  val jobsDeadLettered =
    metrics.counter(
      "jobs_dead_lettered_v2_total",
      "total # of jobs explicitly moved to the dead letter queueName",
      listOf("QueueName"),
    )

  val visibilityTime =
    metrics.histogram(
      "jobs_visibility_time_v2",
      "time that is spent unavailable for the pick-up from the consumer",
      listOf("QueueName"),
    )

  val sqsSendTime =
    metrics.histogram("jobs_sqs_send_latency_v2", "the round trip time to send messages to SQS", listOf("QueueName"))

  val sqsBatchSendTime =
    metrics.histogram(
      "jobs_sqs_batch_send_latency_v2",
      "the round trip time to send batch messages to SQS",
      listOf("QueueName"),
    )

  val batchEnqueueSize =
    metrics.histogram(
      "jobs_sqs_batch_enqueued_size_v2",
      "distribution of batch sizes for SQS enqueue operations",
      listOf("QueueName"),
    )

  val sqsReceiveTime =
    metrics.histogram(
      "jobs_sqs_receive_latency_v2",
      "the round trip time to receive messages from SQS",
      listOf("QueueName"),
    )

  val sqsDeleteTime =
    metrics.histogram(
      "jobs_sqs_delete_latency_v2",
      "the round trip time to delete messages from SQS",
      listOf("QueueName"),
    )

  val queueFirstProcessingLag =
    metrics.histogram(
      "jobs_sqs_first_processing_lag_v2",
      "time it took to receive a job from when it was enqueued",
      listOf("QueueName"),
    )

  val queueProcessingLag =
    metrics.histogram(
      "jobs_sqs_processing_lag_v2",
      "time it took to receive a job from when it was enqueued",
      listOf("QueueName"),
    )

  val channelReceiveLag =
    metrics.histogram(
      "jobs_sqs_channel_receive_lag_v2",
      "time a job spent it the channel between receiver and handler",
      listOf("QueueName"),
    )
}
