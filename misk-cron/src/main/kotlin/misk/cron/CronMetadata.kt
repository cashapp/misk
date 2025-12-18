package misk.cron

import jakarta.inject.Inject
import kotlinx.html.TagConsumer
import misk.moshi.adapter
import misk.tailwind.components.AlertInfoHighlight
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.defaultKotlinMoshi

internal data class CronData(
  val cronEntries: Map<String, CronManager.CronEntry.Metadata>,
  val runningCrons: List<CronManager.RunningCronEntry.Metadata>,
)

internal data class CronMetadata(val cronData: CronData) :
  Metadata(metadata = cronData, prettyPrint = defaultKotlinMoshi.adapter<CronData>().toFormattedJson(cronData)) {
  override fun descriptionBlock(tagConsumer: TagConsumer<*>): TagConsumer<*> =
    tagConsumer.apply {
      AlertInfoHighlight(
        message =
          "Cron job data is still untested in multi-pod deployments and may be inaccurate. This powers the Cron admin dashboard tab.",
        label = "Cron Tab",
        link = "/_admin/cron/",
      )
    }
}

internal class CronMetadataProvider : MetadataProvider<Metadata> {
  @Inject private lateinit var cronManager: CronManager

  override val id: String = "cron"

  override fun get(): Metadata {
    val metadata = cronManager.getMetadata()
    return CronMetadata(metadata)
  }
}
