package misk.hibernate.actions

import com.google.inject.Provider
import jakarta.inject.Inject
import kotlin.reflect.KClass
import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess

internal class DatabaseQueryMetadataProvider<T : DbEntity<T>>(
  val dbEntityClass: KClass<T>,
  val queryClass: KClass<out Query<T>>?,
  val accessAnnotationClass: KClass<out Annotation> = NoAdminDashboardDatabaseAccess::class,
) : Provider<DatabaseQueryMetadata> {
  @Inject lateinit var hibernateDatabaseQueryMetadataFactory: HibernateDatabaseQueryMetadataFactory

  override fun get(): DatabaseQueryMetadata =
    hibernateDatabaseQueryMetadataFactory.fromQuery(
      dbEntityClass = dbEntityClass,
      queryClass = queryClass,
      accessAnnotationClass = accessAnnotationClass,
    )
}
