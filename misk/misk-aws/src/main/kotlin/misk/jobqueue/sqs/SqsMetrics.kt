package misk.jobqueue.sqs

import misk.metrics.Metrics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SqsMetrics @Inject internal constructor(metrics: Metrics) {
  val jobsEnqueued = metrics.counter(
      "jobs_enqueued_total",
      "total # of jobs sent to a queueName",
      listOf("queueName")
  )

  val jobEnqueueFailures = metrics.counter(
      "job_enqueue_failures_total",
      "total # of jobs that failed to enqueue",
      listOf("queueName")
  )

  val jobsReceived = metrics.counter(
      "jobs_received_total",
      "total # of jobs received on a queueName",
      listOf("queueName")
  )

  val handlerDispatchTime = metrics.histogram(
      "job_handler_duration_ms",
      "duration of job handling runs for a given job queueName",
      listOf("queueName")
  )

  val jobsAcknowledged = metrics.counter(
      "jobs_acknowledged_total",
      "total # of jobs acknowledged by handlers",
      listOf("queueName")
  )

  val handlerFailures = metrics.counter(
      "job_handler_failures",
      "total # of jobs whose handlers threw an exception",
      listOf("queueName")
  )

  val jobsDeadLettered = metrics.counter(
      "jobs_dead_lettered",
      "total # of jobs explicitly moved to the dead letter queueName",
      listOf("queueName")
  )
}