package misk.jobqueue.v2

import misk.inject.KAbstractModule
import misk.jobqueue.QueueName
import kotlin.reflect.KClass

class FakeJobHandlerModule private constructor(
  private val queueName: QueueName,
  private val handler: KClass<out JobHandler>,
) : KAbstractModule() {
  override fun configure() {
    newMapBinder<QueueName, JobHandler>().addBinding(queueName).to(handler.java)
  }

  companion object {
    inline fun <reified T: JobHandler> create(queueName: String): FakeJobHandlerModule {
      return create(QueueName(queueName), T::class)
    }

    inline fun <reified T: JobHandler> create(queueName: QueueName): FakeJobHandlerModule {
      return create(queueName, T::class)
    }

    fun create(queueName: QueueName, handler: KClass<out JobHandler>) = FakeJobHandlerModule(queueName, handler)
  }
}
