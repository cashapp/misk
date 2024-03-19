package misk.web.metadata.database

import misk.web.metadata.Metadata
import javax.inject.Inject
import javax.inject.Provider

data class DatabaseMetadata(
  val hibernate: List<DatabaseQueryMetadata>
): Metadata(id = "database-hibernate", metadata = hibernate)

class DatabaseMetadataProvider @Inject constructor() : Provider<DatabaseMetadata> {

  override fun get() = DatabaseMetadata(
    hibernate = TODO()
  )
}
