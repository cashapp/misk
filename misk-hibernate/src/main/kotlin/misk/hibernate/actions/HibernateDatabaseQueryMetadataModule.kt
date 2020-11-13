package misk.hibernate.actions

import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.inject.KAbstractModule
import misk.web.metadata.DatabaseQueryMetadata
import javax.inject.Inject
import kotlin.reflect.KClass

class HibernateDatabaseQueryMetadataModuleFactory @Inject constructor(
  private val metadataFactory: HibernateDatabaseQueryMetadataFactory
) {
  val queriesToBind = mutableListOf<KClass<out Query<*>>>()
  val metadataToBind = mutableListOf<DatabaseQueryMetadata>()

//  data class QueryAccessAnnotationMetadataEntry<T: DbEntity<T>>(
//    val dbEntityClass: KClass<T>,
//    val queryClass: KClass<out Query<T>>,
//    val accessAnnotationClass: KClass<out Annotation>,
//    val metadata: DatabaseQueryMetadata
//  )

  fun <T : DbEntity<T>>addQuery(dbEntityClass: KClass<T>, queryClass: KClass<out Query<T>>, accessAnnotationClass: KClass<out Annotation>? = null) {
    queriesToBind.add(queryClass)
    metadataToBind.add(metadataFactory.fromQuery(dbEntityClass, queryClass, accessAnnotationClass))
  }

  fun buildModule() = object : KAbstractModule() {
    override fun configure() {
      newMultibinder<DatabaseQueryMetadata>()
      metadataToBind.forEach {
        multibind<DatabaseQueryMetadata>().toInstance(it)
      }

    }
  }

//  inline fun <T, reified DB: DbEntity<T>, reified Q: KClass<out Query<T>>, reified AA: KClass<out Annotation>> addQuery() =
//      addQuery<T>(DB::class, Q::class, AA::class)


}