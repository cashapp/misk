package misk

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
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

  @Test fun detectsNonSingletonServices() {
    assertThat(assertFailsWith<com.google.inject.ProvisionException> {
      val injector = Guice.createInjector(
          MiskServiceModule(),
          object: KAbstractModule() {
            override fun configure() {
              multibind<Service>().to<SingletonService1>()
              multibind<Service>().to<SingletonService2>()
              multibind<Service>().to<NonSingletonService2>()
              multibind<Service>().to<NonSingletonService1>()
            }
          }
      )

      injector.getInstance<ServiceManager>()
    }.message).contains("the following services are not marked as @Singleton: " +
        "misk.MiskServiceModuleTest\$NonSingletonService1, misk.MiskServiceModuleTest\$NonSingletonService2")
  }

}