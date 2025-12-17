package misk.aws2.sqs.jobqueue

import software.amazon.awssdk.services.sqs.batchmanager.SqsAsyncBatchManager

fun interface SqsBatchManagerFactory {
  fun get(region: String): SqsAsyncBatchManager
}
