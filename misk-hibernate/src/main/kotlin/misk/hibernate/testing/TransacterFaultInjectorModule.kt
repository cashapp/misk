package misk.hibernate.testing

import com.google.inject.Key
import jakarta.inject.Singleton
import misk.hibernate.HibernateEntityModule
import org.hibernate.event.spi.EventType
import kotlin.reflect.KClass

/**
 * This module creates an event listener for Hibernate with several bindings, to be able to
 * throw errors on transactions.
 */
class TransacterFaultInjectorModule @JvmOverloads constructor(
  private val qualifier: KClass<out Annotation>
) : HibernateEntityModule(qualifier) {

  override fun configureHibernate() {
    val key = Key.get(TransacterFaultInjector::class.java, qualifier.java)

    bind(key)
      .to<TransacterFaultInjector>()
      .`in`(Singleton::class.java)

    bindListener(EventType.SAVE_UPDATE).to(key)
    bindListener(EventType.SAVE).to(key)
    bindListener(EventType.UPDATE).to(key)
    bindListener(EventType.DELETE).to(key)
  }
}

