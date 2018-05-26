package misk.hibernate

import misk.inject.KAbstractModule
import misk.inject.newMultibinder
import kotlin.reflect.KClass

/**
 * Binds a Set<[HibernateEntity]> intended for the [DataSource] annotated by [qualifier].
 */
class HibernateEntityModule(
  private val qualifier: KClass<out Annotation>,
  private val entities: Collection<KClass<*>>
) : KAbstractModule() {

  override fun configure() {
    val multibinder = binder().newMultibinder<HibernateEntity>(qualifier)
    for (entity in entities) {
      multibinder.addBinding().toInstance(HibernateEntity(entity))
    }
  }
}
