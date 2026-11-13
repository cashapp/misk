package misk.hibernate.actions

import com.google.inject.Provider
import jakarta.inject.Inject
import kotlin.reflect.KClass
import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.web.metadata.database.DatabaseQueryMetadata
import misk.web.metadata.database.NoAdminDashboardDatabaseAccess

/**
 * Server-side registration for a single Database Query entity/query. Pairs the browser-facing [DatabaseQueryMetadata]
 * with the [KClass] identities it was built from. This is never serialized: the query actions authorize against
 * [metadata] and run the query against [entityClass]/[queryClass] directly.
 */
internal data class HibernateDatabaseQueryRegistration(
  val metadata: DatabaseQueryMetadata,
  val entityClass: KClass<out DbEntity<*>>,
  /** The static Misk [Query] class, or null for a dynamic query. */
  val queryClass: KClass<out Query<DbEntity<*>>>?,
)

internal class HibernateDatabaseQueryRegistrationProvider<T : DbEntity<T>>(
  private val dbEntityClass: KClass<T>,
  private val queryClass: KClass<out Query<T>>?,
  private val accessAnnotationClass: KClass<out Annotation> = NoAdminDashboardDatabaseAccess::class,
) : Provider<HibernateDatabaseQueryRegistration> {
  @Inject lateinit var hibernateDatabaseQueryMetadataFactory: HibernateDatabaseQueryMetadataFactory

  @Suppress("UNCHECKED_CAST")
  override fun get(): HibernateDatabaseQueryRegistration =
    HibernateDatabaseQueryRegistration(
      metadata =
        hibernateDatabaseQueryMetadataFactory.fromQuery(
          dbEntityClass = dbEntityClass,
          queryClass = queryClass,
          accessAnnotationClass = accessAnnotationClass,
        ),
      entityClass = dbEntityClass,
      queryClass = queryClass as KClass<out Query<DbEntity<*>>>?,
    )
}
