package misk.concurrent

import com.google.common.util.concurrent.ThreadFactoryBuilder
import misk.inject.KAbstractModule
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class ExecutorServiceModule(
  private val annotation: KClass<out Annotation>,
  private val executorService: ExecutorService
) : KAbstractModule() {
  override fun configure() {
    bind<ExecutorService>().annotatedWith(annotation.java).toInstance(executorService)
  }

  companion object {
    fun withFixedThreadPool(
      annotation: KClass<out Annotation>,
      nameFormat: String,
      nThreads: Int
    ): ExecutorServiceModule {
      return ExecutorServiceModule(
          annotation,
          Executors.newFixedThreadPool(
              nThreads,
              ThreadFactoryBuilder().setNameFormat(nameFormat).build()))
    }
  }
}
