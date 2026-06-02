package misk.aws2.sqs.jobqueue

import software.amazon.awssdk.services.sqs.SqsAsyncClient

fun interface SqsClientFactory {
  fun get(region: String): SqsAsyncClient
}
