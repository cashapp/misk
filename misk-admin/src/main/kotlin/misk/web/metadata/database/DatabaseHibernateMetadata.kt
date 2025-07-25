package misk.web.metadata.database

import jakarta.inject.Inject
import kotlinx.html.TagConsumer
import misk.tailwind.components.AlertInfoHighlight
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import misk.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class DatabaseHibernateMetadata(
  val hibernate: List<DatabaseQueryMetadata>
) : Metadata(
  metadata = hibernate,
  prettyPrint = defaultKotlinMoshi
    .adapter<List<DatabaseQueryMetadata>>()
    .toFormattedJson(hibernate),
) {
  override fun descriptionBlock(tagConsumer: TagConsumer<*>): TagConsumer<*> = tagConsumer.apply {
    AlertInfoHighlight(
      message = "Includes metadata on Hibernate MySQL JDBC databases. This powers the Database admin dashboard tab.",
      label = "Admin Dashboard Tab",
      link = "/_admin/database/",
    )
  }
}

internal class DatabaseHibernateMetadataProvider : MetadataProvider<DatabaseHibernateMetadata> {
  @Inject lateinit var metadata: List<DatabaseQueryMetadata>

  override val id: String = "database-hibernate"

  override fun get() = DatabaseHibernateMetadata(hibernate = metadata)
}
