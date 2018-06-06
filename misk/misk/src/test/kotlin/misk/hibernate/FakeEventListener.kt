package misk.hibernate

import org.hibernate.event.spi.PreDeleteEvent
import org.hibernate.event.spi.PreDeleteEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreLoadEvent
import org.hibernate.event.spi.PreLoadEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import javax.inject.Singleton

@Singleton
class FakeEventListener
  : PreLoadEventListener, PreInsertEventListener, PreUpdateEventListener, PreDeleteEventListener {
  private val eventLog = mutableListOf<String>()

  override fun onPreLoad(event: PreLoadEvent) {
    eventLog.add("preload")
  }

  override fun onPreInsert(event: PreInsertEvent): Boolean {
    eventLog.add("preinsert")
    return false
  }

  override fun onPreUpdate(event: PreUpdateEvent): Boolean {
    eventLog.add("preupdate")
    return false
  }

  override fun onPreDelete(event: PreDeleteEvent): Boolean {
    eventLog.add("predelete")
    return false
  }

  /** Removes and returns all recorded events. */
  fun takeEvents(): List<String> {
    val result = eventLog.toList()
    eventLog.clear()
    return result
  }
}