package misk.web.metadata.database

import com.google.inject.Provider
import jakarta.inject.Inject
import misk.web.metadata.Metadata

data class DatabaseHibernateMetadata(
  val hibernate: List<DatabaseQueryMetadata>
) : Metadata(id = "database-hibernate", metadata = hibernate)

class DatabaseHibernateMetadataProvider : Provider<DatabaseHibernateMetadata> {
  @Inject lateinit var metadata: List<DatabaseQueryMetadata>
  override fun get() = DatabaseHibernateMetadata(hibernate = metadata)
}
