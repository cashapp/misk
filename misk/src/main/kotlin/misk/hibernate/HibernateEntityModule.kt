package misk.hibernate

import com.google.inject.Key
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.name.Names
import misk.inject.KAbstractModule
import misk.inject.newMultibinder
import org.hibernate.event.spi.EventType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * Binds hibernate entities and event listeners intended for the [Transacter] annotated by
 * [qualifier].
 */
abstract class HibernateEntityModule(
  private val qualifier: KClass<out Annotation>
) : KAbstractModule() {

  abstract fun configureHibernate()

  override fun configure() {
    // Initialize empty sets for our multibindings.
    binder().newMultibinder<HibernateEntity>(qualifier)
    binder().newMultibinder<HibernateEventListener>(qualifier)

    configureHibernate()
  }

  protected fun addEntities(vararg entities: KClass<out DbEntity<*>>) {
    for (entity in entities) {
      binder().newMultibinder<HibernateEntity>(qualifier)
          .addBinding()
          .toInstance(HibernateEntity(entity))
    }
  }

  protected fun <T> bindListener(type: EventType<T>): LinkedBindingBuilder<in T> {
    // Bind the listener as an anonymous key. We can get the provider for this before its bound!
    val key = Key.get(
        Any::class.java,
        Names.named("HibernateEventListener@${nextHibernateEventListener.getAndIncrement()}")
    )

    // Create a multibinding for a HibernateEventListener that uses the above key.
    binder().newMultibinder<HibernateEventListener>(qualifier)
        .addBinding()
        .toInstance(HibernateEventListener(type, getProvider(key)))

    // Start the binding.
    return bind(key)
  }
}

/** Used to ensure listeners get unique binding keys. This must be unique across all instances. */
private val nextHibernateEventListener = AtomicInteger(1)
