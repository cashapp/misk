package misk

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Provides
import com.google.inject.Scopes
import com.google.inject.Singleton
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.inject.keyOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class ServiceManagerModuleTest {
  @com.google.inject.Singleton
  class SingletonService1 : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  @javax.inject.Singleton
  class SingletonService2 : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class NonSingletonService1 : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class NonSingletonService2 : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class ExplicitEagerSingletonService : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class SingletonScopeService : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class SingletonAnnotationService : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class GoogleSingletonAnnotationService : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class InstanceService : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class ProvidesMethodService : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  @Test fun multibindingServicesThrows() {
    assertThat(assertFailsWith<com.google.inject.ProvisionException> {
      val injector = Guice.createInjector(
          MiskTestingServiceModule(),
          object : KAbstractModule() {
            override fun configure() {
              // Multibinding services is not supported. Expect that we throw for the first service
              // that was bound.
              multibind<Service>().to<SingletonService1>()
              multibind<Service>().to<SingletonService2>()
            }
          }
      )

      injector.getInstance<ServiceManager>()
    }.message).contains("This doesn't work anymore! " +
        "Instead of using `multibind<Service>().to(SingletonService1)`, " +
        "use `install(ServiceModule<SingletonService1>())`.")
  }

  @Test fun detectsNonSingletonServiceEntries() {
    assertThat(assertFailsWith<com.google.inject.ProvisionException> {
      val injector = Guice.createInjector(
          MiskTestingServiceModule(),
          object : KAbstractModule() {
            override fun configure() {
              // Should be recognized as singletons
              install(ServiceModule<SingletonService1>())
              install(ServiceModule<SingletonService2>())
              install(ServiceModule<ProvidesMethodService>())
              install(ServiceModule<InstanceService>())
              bind(keyOf<InstanceService>()).toInstance(
                  InstanceService())
              install(
                  ServiceModule<ExplicitEagerSingletonService>())
              bind(keyOf<ExplicitEagerSingletonService>()).asEagerSingleton()
              install(ServiceModule<SingletonScopeService>())
              bind(keyOf<SingletonScopeService>())
                  .`in`(Scopes.SINGLETON)
              install(ServiceModule<SingletonAnnotationService>())
              bind(keyOf<SingletonAnnotationService>())
                  .`in`(com.google.inject.Singleton::class.java)
              install(
                  ServiceModule<GoogleSingletonAnnotationService>())
              bind(keyOf<GoogleSingletonAnnotationService>())
                  .`in`(com.google.inject.Singleton::class.java)

              // Should be recognized as non-singletons
              install(ServiceModule<NonSingletonService1>())
              install(ServiceModule<NonSingletonService2>())
            }

            @Provides @Singleton
            fun providesSingletonService(): ProvidesMethodService = ProvidesMethodService()
          }
      )

      injector.getInstance<ServiceManager>()
    }.message).contains("the following services are not marked as @Singleton: " +
        "misk.ServiceManagerModuleTest\$NonSingletonService1, " +
        "misk.ServiceManagerModuleTest\$NonSingletonService2")
  }
}
