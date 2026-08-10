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

  val jobsFailedToDeadLetter =
    metrics.counter(
      "jobs_failed_dead_letter_v2_total",
      "total # of jobs that we failed to move to the dead letter queueName",
      listOf("QueueName"),
    )

  val jobsFailedToRetryWithBackoff =
    metrics.counter(
      "jobs_failed_retry_with_backoff_v2_total",
      "total # of jobs that we failed to retry with backoff",
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

  val sqsReceiveFailures =
    metrics.counter(
      "jobs_sqs_receive_failures_v2_total",
      "total # of failed requests to receive messages from SQS",
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

  val drainsStarted =
    metrics.counter(
      "jobs_sqs_drains_started_v2_total",
      "total # of consumer shutdown drains started",
      listOf("QueueName"),
    )

  val drainInFlightAtStart =
    metrics.histogram(
      "jobs_sqs_drain_in_flight_at_start_v2",
      "# of active and queued-handoff jobs when a shutdown drain started",
      listOf("QueueName"),
    )

  val drainJobsCompleted =
    metrics.counter(
      "jobs_sqs_drain_jobs_completed_v2_total",
      "total # of jobs that finished handling during a shutdown drain, by handler result " +
        "(ok, retry_later, retry_with_backoff, dead_letter, handler_failed)",
      listOf("QueueName", "result"),
    )

  val drainJobsCancelled =
    metrics.counter(
      "jobs_sqs_drain_jobs_cancelled_v2_total",
      "total # of in-flight jobs cancelled because the shutdown drain deadline passed",
      listOf("QueueName"),
    )

  val drainDuration =
    metrics.histogram(
      "jobs_sqs_drain_duration_ms_v2",
      "duration of consumer shutdown drains by terminal result",
      listOf("QueueName", "result"),
    )

  val receiveAttemptsAfterDrain =
    metrics.counter(
      "jobs_sqs_receive_attempts_after_drain_v2_total",
      "total # of SQS receive attempts made after a shutdown drain started",
      listOf("QueueName"),
    )

  val drainAcksSucceeded =
    metrics.counter(
      "jobs_sqs_drain_acknowledged_v2_total",
      "total # of jobs acknowledged during a shutdown drain",
      listOf("QueueName"),
    )

  val drainAckFailures =
    metrics.counter(
      "jobs_sqs_drain_ack_failures_v2_total",
      "total # of jobs that failed to acknowledge during a shutdown drain",
      listOf("QueueName"),
    )
}
