package misk.embedded.sample.embedded

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Injector
import misk.embedded.InjectorFactory
import misk.inject.KAbstractModule

class EmbeddedSampleServiceInjectorFactory : InjectorFactory {
  override fun createInjector(): Injector {
    return Guice.createInjector(object : KAbstractModule() {
      override fun configure() {
        bind<ServiceManager>().toInstance(ServiceManager(listOf(object : AbstractIdleService() {
          override fun startUp() {
            println("starting up")
          }

          override fun shutDown() {
            println("shutting down")
          }
        })))

        bind<EmbeddedSampleService>().to<RealEmbeddedSampleService>()
      }
    })
  }
}
