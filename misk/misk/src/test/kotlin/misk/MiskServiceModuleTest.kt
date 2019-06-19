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

internal class MiskServiceModuleTest {
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

  @Test fun detectsNonSingletonServices() {
    assertThat(assertFailsWith<com.google.inject.ProvisionException> {
      val injector = Guice.createInjector(
          MiskTestingServiceModule(),
          object : KAbstractModule() {
            override fun configure() {
              // Should be recognized as singletons
              multibind<Service>().to<SingletonService1>()
              multibind<Service>().to<SingletonService2>()
              multibind<Service>().to<ProvidesMethodService>()
              multibind<Service>().toInstance(InstanceService())
              multibind<Service>().to<ExplicitEagerSingletonService>().asEagerSingleton()
              multibind<Service>().to<SingletonScopeService>()
                  .`in`(Scopes.SINGLETON)
              multibind<Service>().to<SingletonAnnotationService>()
                  .`in`(com.google.inject.Singleton::class.java)
              multibind<Service>().to<GoogleSingletonAnnotationService>()
                  .`in`(com.google.inject.Singleton::class.java)

              // Should be recognized as non-singletons
              multibind<Service>().to<NonSingletonService2>()
              multibind<Service>().to<NonSingletonService1>()
            }

            @Provides @Singleton
            fun providesSingletonService(): ProvidesMethodService = ProvidesMethodService()
          }
      )

      injector.getInstance<ServiceManager>()
    }.message).contains("the following services are not marked as @Singleton: " +
        "misk.MiskServiceModuleTest\$NonSingletonService1, " +
        "misk.MiskServiceModuleTest\$NonSingletonService2")
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
              bind(keyOf<InstanceService>()).toInstance(InstanceService())
              install(ServiceModule<ExplicitEagerSingletonService>())
              bind(keyOf<ExplicitEagerSingletonService>()).asEagerSingleton()
              install(ServiceModule<SingletonScopeService>())
              bind(keyOf<SingletonScopeService>())
                  .`in`(Scopes.SINGLETON)
              install(ServiceModule<SingletonAnnotationService>())
              bind(keyOf<SingletonAnnotationService>())
                  .`in`(com.google.inject.Singleton::class.java)
              install(ServiceModule<GoogleSingletonAnnotationService>())
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
        "misk.MiskServiceModuleTest\$NonSingletonService1, " +
        "misk.MiskServiceModuleTest\$NonSingletonService2")
  }
}
