package misk.jobqueue

import kotlin.reflect.KClass
import misk.inject.KAbstractModule

class FakeJobHandlerModule<T : JobHandler>
private constructor(private val queueName: QueueName, private val handler: KClass<T>) : KAbstractModule() {

  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)
    newMapBinder<QueueName, BatchJobHandler>()
  }

  companion object {
    inline fun <reified T : JobHandler> create(queueName: QueueName): FakeJobHandlerModule<T> =
      create(queueName, T::class)

    @JvmStatic
    fun <T : JobHandler> create(queueName: QueueName, handlerClass: Class<T>): FakeJobHandlerModule<T> {
      return create(queueName, handlerClass.kotlin)
    }

    /** Returns a module that registers a handler for a fake job queue. */
    fun <T : JobHandler> create(queueName: QueueName, handlerClass: KClass<T>): FakeJobHandlerModule<T> {
      return FakeJobHandlerModule(queueName, handlerClass)
    }
  }
}
