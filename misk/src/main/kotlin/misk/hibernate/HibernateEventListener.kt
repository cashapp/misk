package misk.hibernate

import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.PreDeleteEvent
import org.hibernate.event.spi.PreDeleteEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreLoadEvent
import org.hibernate.event.spi.PreLoadEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import javax.inject.Provider

/**
 * A registration of a listener for one of many Hibernate event types. This class uses providers to
 * get a new listener instance each time it is needed. This is intended to prevent circular
 * dependencies.
 */
internal class HibernateEventListener(
  val type: EventType<*>,
  val provider: Provider<*>
) : PreLoadEventListener,
    PreDeleteEventListener,
    PreUpdateEventListener,
    PreInsertEventListener {
  init {
    when (type) {
      EventType.PRE_LOAD -> Unit
      EventType.PRE_INSERT -> Unit
      EventType.PRE_UPDATE -> Unit
      EventType.PRE_DELETE -> Unit
      else -> throw UnsupportedOperationException("$type not currently supported")
    }
  }

  fun register(eventListenerRegistry: EventListenerRegistry) {
    @Suppress("UNCHECKED_CAST") // We don't have static type information for the event type.
    eventListenerRegistry.appendListeners(type as EventType<Any>, this)
  }

  override fun onPreLoad(event: PreLoadEvent) =
      (provider.get() as PreLoadEventListener).onPreLoad(event)

  override fun onPreDelete(event: PreDeleteEvent) =
      (provider.get() as PreDeleteEventListener).onPreDelete(event)

  override fun onPreUpdate(event: PreUpdateEvent) =
      (provider.get() as PreUpdateEventListener).onPreUpdate(event)

  override fun onPreInsert(event: PreInsertEvent) =
      (provider.get() as PreInsertEventListener).onPreInsert(event)
}