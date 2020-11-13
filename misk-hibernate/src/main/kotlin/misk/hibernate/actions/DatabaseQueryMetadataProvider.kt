package misk.hibernate.actions

import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.web.metadata.DatabaseQueryMetadata
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

class DatabaseQueryMetadataProvider<T : DbEntity<T>>(
  val dbEntityClass: KClass<T>,
  val queryClass: KClass<out Query<T>>,
  val accessAnnotationClass: KClass<out Annotation>? = null
) : Provider<DatabaseQueryMetadata> {
  @Inject lateinit var hibernateDatabaseQueryMetadataFactory: HibernateDatabaseQueryMetadataFactory
  override fun get(): DatabaseQueryMetadata = hibernateDatabaseQueryMetadataFactory.fromQuery(
      dbEntityClass = dbEntityClass,
      queryClass = queryClass,
      accessAnnotationClass = accessAnnotationClass
  )
}