package misk.hibernate.actions

import misk.hibernate.DbEntity
import misk.hibernate.HibernateEntity
import misk.hibernate.Query
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.DatabaseQueryMetadata
import kotlin.reflect.KClass

abstract class HibernateDatabaseQueryMetadataModule : KAbstractModule() {
  abstract fun configureHibernate()

  protected fun <T : DbEntity<T>> addQuery(
    dbEntityClass: KClass<T>,
    queryClass: KClass<out Query<T>>,
    accessAnnotationClass: KClass<out Annotation>? = null
  ) = apply {
    multibind<DatabaseQueryMetadata>().toProvider(DatabaseQueryMetadataProvider(
        dbEntityClass = dbEntityClass,
        queryClass = queryClass,
        accessAnnotationClass = accessAnnotationClass
    ))
    multibind<HibernateQuery>().toInstance(HibernateQuery(queryClass as KClass<out Query<DbEntity<*>>>))
  }

  override fun configure() {
    newMultibinder<HibernateEntity>()
    newMultibinder<DatabaseQueryMetadata>()

    configureHibernate()

    install(WebActionModule.create<HibernateDatabaseQueryAction>())
  }
}