package misk.jobqueue

import jakarta.inject.Inject
import misk.inject.KInstallOnceModule
import misk.inject.typeLiteral
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataModule
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class JobHandlerMetadata(
  val handlerClass: String,
  val supertypes: List<String>,
)

internal data class JobqueueMetadata(
  val queues: Map<String, JobHandlerMetadata>
): Metadata(
  metadata = queues,
  prettyPrint = defaultKotlinMoshi
    .adapter<Map<String, JobHandlerMetadata>>()
    .toFormattedJson(queues),
  descriptionString = "Job queues and their registered handlers."
  )

internal class JobqueueMetadataProvider: MetadataProvider<JobqueueMetadata> {
  @Inject lateinit var queues: Map<QueueName, JobHandler>

  override val id = "job-queue"

  override fun get(): JobqueueMetadata {
    return JobqueueMetadata(queues = queues.entries
      .associate { it.key.value to JobHandlerMetadata(
        handlerClass = it.value::class.qualifiedName!!,
        supertypes = it.value::class.supertypes
          .map { it.toString() }
          // Every class implements Any so not useful to display that in the metadata
          .filterNot { it == Any::class.qualifiedName }
      ) }
      .toSortedMap())
  }
}

// Dedicated module to ensure install once semantics since it is installed as part of the
// AwsSqsJobHandlerModule module which is installed multiple times.
class JobqueueMetadataModule: KInstallOnceModule() {
  override fun configure() {
    install(MetadataModule(JobqueueMetadataProvider()))
  }
}
