package misk.hibernate

import com.google.common.collect.LinkedHashMultimap
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
 * This class delegates to other event listeners registered with Guice. This allows us to defer
 * providing the listener instance until its event arrives.
 */
internal class AggregateListener(
  registrations: Set<ListenerRegistration>
) : PreLoadEventListener,
    PreDeleteEventListener,
    PreUpdateEventListener,
    PreInsertEventListener {
  private val multimap = LinkedHashMultimap.create<EventType<*>, Provider<*>>()!!

  init {
    for (eventTypeAndListener in registrations) {
      multimap.put(eventTypeAndListener.type, eventTypeAndListener.provider)
    }
  }

  fun registerAll(eventListenerRegistry: EventListenerRegistry) {
    for (eventType in multimap.keySet()) {
      @Suppress("UNCHECKED_CAST") // We don't have static type information for the event type.
      eventListenerRegistry.appendListeners(eventType as EventType<Any>, this)
    }
  }

  override fun onPreLoad(event: PreLoadEvent) {
    for (provider in multimap[EventType.PRE_LOAD]) {
      (provider.get() as PreLoadEventListener).onPreLoad(event)
    }
  }

  override fun onPreDelete(event: PreDeleteEvent): Boolean {
    var veto = false
    for (provider in multimap[EventType.PRE_DELETE]) {
      veto = veto or (provider.get() as PreDeleteEventListener).onPreDelete(event)
    }
    return veto
  }

  override fun onPreUpdate(event: PreUpdateEvent): Boolean {
    var veto = false
    for (provider in multimap[EventType.PRE_UPDATE]) {
      veto = veto or (provider.get() as PreUpdateEventListener).onPreUpdate(event)
    }
    return veto
  }

  override fun onPreInsert(event: PreInsertEvent): Boolean {
    var veto = false
    for (provider in multimap[EventType.PRE_INSERT]) {
      veto = veto or (provider.get() as PreInsertEventListener).onPreInsert(event)
    }
    return veto
  }
}