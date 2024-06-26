package misk.jobqueue.sqs

import jakarta.inject.Inject
import misk.jobqueue.JobHandler
import misk.jobqueue.QueueName
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class SqsJobqueueMetadata(
  val queues: Map<QueueName, String>
): Metadata(
  metadata = queues,
  prettyPrint = defaultKotlinMoshi
    .adapter<Map<QueueName, String>>()
    .toFormattedJson(queues),
  )

internal class SqsJobqueueMetadataProvider: MetadataProvider<SqsJobqueueMetadata> {
  @Inject lateinit var queues: Map<QueueName, JobHandler>

  override val id = "sqs-job-queue"

  override fun get(): SqsJobqueueMetadata {
    return SqsJobqueueMetadata(queues = queues.mapValues { it.value::class.qualifiedName!! })
  }
}
