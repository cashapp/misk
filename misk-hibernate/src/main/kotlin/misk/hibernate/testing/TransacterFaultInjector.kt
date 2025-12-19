package misk.hibernate.testing

import jakarta.inject.Inject
import misk.testing.FakeFixture
import org.hibernate.event.spi.DeleteEvent
import org.hibernate.event.spi.DeleteEventListener
import org.hibernate.event.spi.SaveOrUpdateEvent
import org.hibernate.event.spi.SaveOrUpdateEventListener

/**
 * This [TransacterFaultInjector] is used for controlling transaction failures within mySQL. It uses Hibernates Event
 * Listeners to hook into save/update/delete transactions and throw erros when instructed.
 *
 * To use, in your test class,
 *
 * `install(TransacterFaultInjectorModule(MyDbClass::class))`
 */
class TransacterFaultInjector @Inject constructor() : FakeFixture(), SaveOrUpdateEventListener, DeleteEventListener {
  private val enqueuedExceptions by resettable { mutableListOf<Exception?>() }

  /**
   * Enqueues a no-throw operation. Is not required for normal operation. Only required when a pattern of exceptions &
   * successes are needed.
   *
   * @param times How many times should the error be thrown. Useful for retries.
   */
  @JvmOverloads
  fun enqueueNoThrow(times: Int = 1) {
    for (i in 0 until times) {
      enqueuedExceptions.add(null)
    }
  }

  /**
   * Enqueues an error to be thrown on the next transaction.
   *
   * @param error: The error to throw when a transaction is made.
   * @param times How many times should the error be thrown. Useful for retries.
   */
  @JvmOverloads
  fun enqueueThrow(error: Exception, times: Int = 1) {
    for (i in 0 until times) {
      enqueuedExceptions.add(error)
    }
  }

  private fun throwNextException() {
    if (enqueuedExceptions.isNotEmpty()) {
      val exception = enqueuedExceptions.removeFirst()

      // a null exception to throw means transaction should function normally
      if (exception != null) {
        throw exception
      }
    }
  }

  override fun onSaveOrUpdate(event: SaveOrUpdateEvent) {
    throwNextException()
  }

  override fun onDelete(event: DeleteEvent?) {
    throwNextException()
  }

  override fun onDelete(event: DeleteEvent?, transientEntities: MutableSet<Any?>?) {
    throwNextException()
  }
}
