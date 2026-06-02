package misk.hibernate

import com.google.inject.Provider
import org.hibernate.event.spi.EventType

/** Control how we register listeners. */
enum class BindPolicy {
  PREPEND,
  REPLACE,
  APPEND,
}

/**
 * A registration of a listener for one of many Hibernate event types. This class uses providers to get a new listener
 * instance each time it is needed. This is intended to prevent circular dependencies.
 */
internal class ListenerRegistration(val type: EventType<*>, val provider: Provider<*>, val policy: BindPolicy) {
  init {
    when (type) {
      EventType.PRE_LOAD -> Unit
      EventType.POST_LOAD -> Unit
      EventType.PRE_INSERT -> Unit
      EventType.POST_INSERT -> Unit
      EventType.PRE_UPDATE -> Unit
      EventType.POST_UPDATE -> Unit
      EventType.PRE_DELETE -> Unit
      EventType.POST_DELETE -> Unit
      EventType.SAVE_UPDATE -> Unit
      EventType.UPDATE -> Unit
      EventType.SAVE -> Unit
      EventType.DELETE -> Unit
      EventType.FLUSH_ENTITY -> Unit
      else -> throw UnsupportedOperationException("$type not currently supported")
    }
  }
}
