package misk.hibernate

import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TimestampListener @Inject constructor() : PreInsertEventListener,
  PreUpdateEventListener {
  @Inject lateinit var clock: Clock

  override fun onPreInsert(event: PreInsertEvent): Boolean {
    val entity = event.entity
    if (entity is DbTimestampedEntity) {
      val now = clock.instant()!!
      entity.created_at = now
      entity.updated_at = now
      event.state[event.persister.propertyNames.indexOf("created_at")] = now
      event.state[event.persister.propertyNames.indexOf("updated_at")] = now
    }
    return false
  }

  override fun onPreUpdate(event: PreUpdateEvent): Boolean {
    val entity = event.entity
    if (entity is DbTimestampedEntity) {
      val now = clock.instant()!!
      entity.updated_at = now
      event.state[event.persister.propertyNames.indexOf("updated_at")] = now
    }
    return false
  }
}
