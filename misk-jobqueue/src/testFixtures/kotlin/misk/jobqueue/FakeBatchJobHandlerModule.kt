package misk.jobqueue

import misk.inject.KAbstractModule
import kotlin.reflect.KClass

class FakeBatchJobHandlerModule<T : BatchJobHandler> private constructor(
  private val queueName: QueueName,
  private val handler: KClass<T>
) : KAbstractModule() {

  override fun configure() {
    newMapBinder<QueueName, BatchJobHandler>().addBinding(queueName).to(handler.java)
    newMapBinder<QueueName, JobHandler>()
  }

  companion object {
    inline fun <reified T : BatchJobHandler> create(queueName: QueueName):
      FakeBatchJobHandlerModule<T> = create(queueName, T::class)

    @JvmStatic
    fun <T : BatchJobHandler> create(
      queueName: QueueName,
      handlerClass: Class<T>
    ): FakeBatchJobHandlerModule<T> {
      return create(queueName, handlerClass.kotlin)
    }

    /**
     * Returns a module that registers a batch handler for a fake job queue.
     */
    fun <T : BatchJobHandler> create(
      queueName: QueueName,
      handlerClass: KClass<T>
    ): FakeBatchJobHandlerModule<T> {
      return FakeBatchJobHandlerModule(queueName, handlerClass)
    }
  }
}
