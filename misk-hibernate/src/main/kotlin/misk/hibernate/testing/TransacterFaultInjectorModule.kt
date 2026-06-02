package misk.hibernate.testing

import com.google.inject.Key
import kotlin.reflect.KClass
import misk.hibernate.HibernateEntityModule
import misk.inject.asSingleton
import org.hibernate.event.spi.EventType

/**
 * This module creates an event listener for Hibernate with several bindings, to be able to throw errors on
 * transactions.
 */
class TransacterFaultInjectorModule @JvmOverloads constructor(private val qualifier: KClass<out Annotation>) :
  HibernateEntityModule(qualifier) {

  override fun configureHibernate() {
    val key = Key.get(TransacterFaultInjector::class.java, qualifier.java)

    bind(key).to<TransacterFaultInjector>().asSingleton()

    bindListener(EventType.SAVE_UPDATE).to(key)
    bindListener(EventType.SAVE).to(key)
    bindListener(EventType.UPDATE).to(key)
    bindListener(EventType.DELETE).to(key)
  }
}
