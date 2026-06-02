package misk.inject.guice7

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.Inject
import com.google.inject.Injector
import java.util.concurrent.TimeUnit
import misk.MiskTestingServiceModule
import misk.inject.KInstallOnceModule
import misk.inject.getInstance
import misk.inject.keyOf
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class Guice7IntegrationTest {
  private lateinit var injector: Injector

  @BeforeEach
  fun setupInjector() {
    injector = Guice.createInjector(Guice7TestModule)
  }

  @Test
  fun `basic injections work in guice 7`() {
    val testInstanceProvider = injector.getProvider(keyOf<Guice7TestInterface>())
    assertThat(testInstanceProvider.get()).isNotNull()
  }

  @Test
  fun `multibind injections work in guice 7`() {
    val instance = injector.getInstance<WithMultibind>()
    assertThat(instance.multiboundInstances).hasSize(2)
  }

  @Test
  fun `simple actions with guice 7`() {
    val injector =
      Guice.createInjector(
        WebServerTestingModule(),
        MiskTestingServiceModule(),
        WebActionModule.create<CountHypeAction>(),
      )

    val serviceManager = injector.getInstance<ServiceManager>()
    serviceManager.startAsync().awaitHealthy(30, TimeUnit.SECONDS)

    try {
      val webTestClient = injector.getInstance<WebTestClient>()
      val response = webTestClient.post(path = "/count", body = "[\"foo\",\"miskhype\",\"miskhype\"]").response
      assertThat(response.body?.string()?.toInt()).isEqualTo(2)
    } finally {
      serviceManager.stopAsync().awaitStopped(60, TimeUnit.SECONDS)
    }
  }
}

private object Guice7TestModule : KInstallOnceModule() {
  override fun configure() {
    binder().requireAtInjectOnConstructors()

    bind<Guice7TestInterface>().to<Guice7TestClass>()
    bind<Guice7TestClass.GuiceConstructor>()
    bind<Guice7TestClass.JakartaConstructor>()

    multibind<Guice7TestInterface.Multibind>().to<Guice7TestClass.GuiceConstructor>()
    multibind<Guice7TestInterface.Multibind>().to<Guice7TestClass.JakartaConstructor>()
  }
}

private class WithMultibind @Inject constructor(val multiboundInstances: List<Guice7TestInterface.Multibind>)

internal open class CountHypeAction @Inject constructor() : WebAction {
  @Post("/count")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun postMiskHype(@RequestBody request: List<String>): Int = request.count { it == "miskhype" }
}
