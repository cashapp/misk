package misk.jobqueue.sqs

import misk.metrics.Metrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQS Jobqueue metrics.
 *
 * NB: we use the capitalized "QueueName" label to stay consistent with SQS' similarly named label. This lets us filter
 * for queues both client-side and on SQS with the same label.
 */
@Singleton
internal class SqsMetrics @Inject internal constructor(metrics: Metrics) {
  val jobsEnqueued = metrics.counter(
    "jobs_enqueued_total",
    "total # of jobs sent to a queueName",
    // Duplicate labels so we don't break existing dashboards/detectors
    // TODO(hamdan): remove queueName once no one's using it
    listOf("queueName", "QueueName")
  )

  val jobEnqueueFailures = metrics.counter(
    "job_enqueue_failures_total",
    "total # of jobs that failed to enqueue",
    listOf("queueName", "QueueName")
  )

  val jobsReceived = metrics.counter(
    "jobs_received_total",
    "total # of jobs received on a queueName",
    listOf("queueName", "QueueName")
  )

  val handlerDispatchTime = metrics.histogram(
    "job_handler_duration_ms",
    "duration of job handling runs for a given job queueName",
    listOf("queueName", "QueueName")
  )

  val jobsAcknowledged = metrics.counter(
    "jobs_acknowledged_total",
    "total # of jobs acknowledged by handlers",
    listOf("queueName", "QueueName")
  )

  val handlerFailures = metrics.counter(
    "job_handler_failures",
    "total # of jobs whose handlers threw an exception",
    listOf("queueName", "QueueName")
  )

  val jobsDeadLettered = metrics.counter(
    "jobs_dead_lettered",
    "total # of jobs explicitly moved to the dead letter queueName",
    listOf("queueName", "QueueName")
  )

  val sqsSendTime = metrics.histogram(
    "jobs_sqs_send_latency",
    "the round trip time to send messages to SQS",
    listOf("queueName", "QueueName")
  )

  val sqsReceiveTime = metrics.histogram(
    "jobs_sqs_receive_latency",
    "the round trip time to receive messages from SQS",
    listOf("queueName", "QueueName")
  )

  val sqsDeleteTime = metrics.histogram(
    "jobs_sqs_delete_latency",
    "the round trip time to delete messages from SQS",
    listOf("queueName", "QueueName")
  )

  val sqsApproxNumberOfMessages = metrics.gauge(
    "ApproximateNumberOfMessagesVisible",
    "the approximate number of messages available for retrieval from SQS",
    // `namespace` and `stat` is to emulate the CloudWatch metrics.
    listOf("namespace", "stat", "queueName", "QueueName")
  )

  val sqsApproxNumberOfMessagesNotVisible = metrics.gauge(
    "ApproximateNumberOfMessagesNotVisible",
    "the  approximate number of messages that are in flight. Messages are considered to " +
      "be in flight if they have been sent to a client but have not yet been deleted or have " +
      "not yet reached the end of their visibility window.",
    // `namespace` and `stat` is to emulate the CloudWatch metrics.
    listOf("namespace", "stat", "queueName", "QueueName")
  )
}
