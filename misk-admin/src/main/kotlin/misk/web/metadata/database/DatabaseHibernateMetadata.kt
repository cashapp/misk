package misk.web.metadata.database

import misk.web.metadata.Metadata
import javax.inject.Inject
import javax.inject.Provider

data class DatabaseHibernateMetadata(
  val hibernate: List<DatabaseQueryMetadata>
) : Metadata(id = "database-hibernate", metadata = hibernate)

class DatabaseHibernateMetadataProvider @Inject constructor() : Provider<DatabaseHibernateMetadata> {
  @Inject lateinit var metadata: List<DatabaseQueryMetadata>
  override fun get() = DatabaseHibernateMetadata(hibernate = metadata)
}
