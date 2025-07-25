package misk.cron

import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import misk.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class CronData(
  val cronEntries: Map<String, CronManager.CronEntry.Metadata>,
  val runningCrons: List<String>
)

internal data class CronMetadata(
  val cronData: CronData,
): Metadata(
  metadata = cronData,
  prettyPrint = defaultKotlinMoshi
    .adapter<CronData>()
    .toFormattedJson(cronData),
  descriptionString = "Cron job data is still untested in multi-pod deployments and may be inaccurate."
)

internal class CronMetadataProvider : MetadataProvider<Metadata> {
  @Inject private lateinit var cronManager: CronManager

  override val id: String = "cron"

  override fun get(): Metadata {
    val metadata = cronManager.getMetadata()
    return CronMetadata(metadata)
  }
}
