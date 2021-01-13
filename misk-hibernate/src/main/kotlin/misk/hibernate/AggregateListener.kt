package misk.hibernate

import com.google.common.collect.LinkedHashMultimap
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.event.spi.PostDeleteEventListener
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.PostLoadEvent
import org.hibernate.event.spi.PostLoadEventListener
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.event.spi.PreDeleteEvent
import org.hibernate.event.spi.PreDeleteEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreLoadEvent
import org.hibernate.event.spi.PreLoadEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import org.hibernate.event.spi.SaveOrUpdateEvent
import org.hibernate.event.spi.SaveOrUpdateEventListener
import org.hibernate.persister.entity.EntityPersister
import javax.inject.Provider

/**
 * This class delegates to other event listeners registered with Guice. This allows us to defer
 * providing the listener instance until its event arrives.
 */
internal class AggregateListener(
  registrations: Set<ListenerRegistration>
) : PreLoadEventListener,
    PostLoadEventListener,
    PreDeleteEventListener,
    PostDeleteEventListener,
    PreUpdateEventListener,
    PostUpdateEventListener,
    PreInsertEventListener,
    PostInsertEventListener,
    SaveOrUpdateEventListener {
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

  override fun requiresPostCommitHanding(persister: EntityPersister?): Boolean {
    var veto = false
    for (provider in multimap[EventType.POST_INSERT]) {
      veto = veto or (provider.get() as PostInsertEventListener).requiresPostCommitHandling(persister)
    }
    for (provider in multimap[EventType.POST_UPDATE]) {
      veto = veto or (provider.get() as PostUpdateEventListener).requiresPostCommitHandling(persister)
    }
    for (provider in multimap[EventType.POST_DELETE]) {
      veto = veto or (provider.get() as PostDeleteEventListener).requiresPostCommitHandling(persister)
    }
    return veto
  }

  override fun onPostDelete(event: PostDeleteEvent?) {
    for (provider in multimap[EventType.POST_DELETE]) {
      (provider.get() as PostDeleteEventListener).onPostDelete(event)
    }
  }

  override fun onPostUpdate(event: PostUpdateEvent?) {
    for (provider in multimap[EventType.POST_UPDATE]) {
      (provider.get() as PostUpdateEventListener).onPostUpdate(event)
    }
  }

  override fun onPostLoad(event: PostLoadEvent?) {
    for (provider in multimap[EventType.POST_LOAD]) {
      (provider.get() as PostLoadEventListener).onPostLoad(event)
    }
  }

  override fun onPostInsert(event: PostInsertEvent?) {
    for (provider in multimap[EventType.POST_INSERT]) {
      (provider.get() as PostInsertEventListener).onPostInsert(event)
    }
  }

  override fun onSaveOrUpdate(event: SaveOrUpdateEvent?) {
    for (provider in multimap[EventType.SAVE_UPDATE]) {
      (provider.get() as SaveOrUpdateEventListener).onSaveOrUpdate(event)
    }
  }
}
