package misk.web.metadata.database

import com.squareup.moshi.JsonAdapter
import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

data class DatabaseHibernateMetadata(
  override val metadata: List<DatabaseQueryMetadata>,
  override val adapter: JsonAdapter<List<DatabaseQueryMetadata>> = defaultKotlinMoshi.adapter<List<DatabaseQueryMetadata>>(),
) : Metadata<List<DatabaseQueryMetadata>>

class DatabaseHibernateMetadataProvider : MetadataProvider<List<DatabaseQueryMetadata>, DatabaseHibernateMetadata> {
  @Inject lateinit var metadata: List<DatabaseQueryMetadata>

  override val id: String = "database-hibernate"

  override fun get() = DatabaseHibernateMetadata(metadata = metadata)
}
