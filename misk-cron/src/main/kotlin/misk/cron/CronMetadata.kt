package misk.cron

import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class CronMetadata(
  val cronEntries: Map<String, CronManager.CronEntry.Metadata>,
  val runningCrons: List<String>
)

internal class CronMetadataProvider : MetadataProvider<Metadata> {
  @Inject private lateinit var cronManager: CronManager

  override val id: String = "cron"

  override fun get(): Metadata {
    val metadata = cronManager.getMetadata()
    return Metadata(
      metadata = metadata,
      prettyPrint = defaultKotlinMoshi
        .adapter<CronMetadata>()
        .toFormattedJson(metadata)
    )
  }
}
