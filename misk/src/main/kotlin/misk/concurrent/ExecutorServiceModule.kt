package misk.concurrent

import com.google.inject.Provider
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Install this to bind an executor service with [annotation]. The executor service will be
 * automatically shut down when the service shuts down.
 */
class ExecutorServiceModule(
  private val annotation: KClass<out Annotation>,
  private val createFunction: (ExecutorServiceFactory) -> ExecutorService
) : KAbstractModule() {
  override fun configure() {
    bind<ExecutorService>()
      .annotatedWith(annotation.java)
      .toProvider(object : Provider<ExecutorService> {
        @Inject lateinit var executorServiceFactory: ExecutorServiceFactory

        override fun get() = createFunction(executorServiceFactory)
      })
      .asSingleton()
  }

  companion object {
    fun withFixedThreadPool(
      annotation: KClass<out Annotation>,
      nameFormat: String,
      nThreads: Int
    ) = ExecutorServiceModule(annotation) { it.fixed(nameFormat, nThreads) }

    fun withUnboundThreadPool(
      annotation: KClass<out Annotation>,
      nameFormat: String
    ) = ExecutorServiceModule(annotation) { it.unbounded(nameFormat) }
  }
}
