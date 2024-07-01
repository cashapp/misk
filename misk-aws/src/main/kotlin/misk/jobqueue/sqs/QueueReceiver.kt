package misk.jobqueue.sqs

import misk.tasks.Status

internal interface QueueReceiver {
  fun stop()
  fun run(): Status
}
