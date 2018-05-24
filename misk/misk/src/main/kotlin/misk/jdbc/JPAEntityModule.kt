package misk.jdbc

import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.newMultibinder
import kotlin.reflect.KClass

/**
 * Binds a Set<[JPAEntity]> intended for the [DataSource] annotated by [annotatedBy].
 */
class JPAEntityModule (
  private val annotatedBy: Class<out Annotation>? = null,
  private val entities: Set<Class<*>>
) : KAbstractModule() {

  override fun configure() {
    binder().newMultibinder<JPAEntity>(annotatedBy)

    for (entity in entities) {
      binder().addMultibinderBinding<JPAEntity>(annotatedBy).toInstance(JPAEntity(entity))
    }
  }

  companion object {
    fun create(entities: Set<Class<*>>) = create(null, entities)

    fun create(annotatedBy: Annotation?, entities: Set<Class<*>>) =
      create(annotatedBy?.let { it::class.java }, entities)

    fun <A : Annotation> create(annotatedBy: Class<A>?, entities: Set<Class<*>>) =
      JPAEntityModule(annotatedBy, entities.toSet())

    @JvmName("-createKClass")
    fun create(entities: Set<KClass<*>>) = create(null, entities)

    @JvmName("-createKClass")
    fun create(annotatedBy: Annotation?, entities: Set<KClass<*>>) =
      create(annotatedBy?.let { it::class }, entities)

    fun <A : Annotation> create(annotatedBy: KClass<A>?, entities: Set<KClass<*>>) =
      JPAEntityModule(annotatedBy?.java, entities.map { it.java }.toSet())
  }
}
