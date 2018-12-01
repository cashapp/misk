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
          MiskServiceModule(),
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
        "misk.MiskServiceModuleTest\$NonSingletonService1, misk.MiskServiceModuleTest\$NonSingletonService2")
  }
}