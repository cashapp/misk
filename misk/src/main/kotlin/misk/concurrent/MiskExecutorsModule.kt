package misk.concurrent

import misk.inject.KAbstractModule

internal class ExecutorsModule : KAbstractModule() {
  override fun configure() {
    bind<ExecutorServiceFactory>().to<RealExecutorServiceFactory>()
  }
}
