package misk.web.ssl

import com.google.inject.Guice
import com.google.inject.Provides
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class WebActionInterfaceFunctionsTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService

  private lateinit var client: OkHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    client = clientInjector.getInstance()
  }

  @Test
  fun happyPath() {
    val call = client.newCall(
      Request.Builder()
        .url(jetty.httpServerUrl.resolve("/hello")!!)
        .build()
    )
    call.execute().use { response ->
      assertThat(response.body.string()).isEqualTo("hello")
    }
  }

  @Test
  fun actionAnnotationsOnInterfaceFunction() {
    val call = client.newCall(
      Request.Builder()
        .url(jetty.httpServerUrl.resolve("/greet")!!)
        .build()
    )
    call.execute().use { response ->
      assertThat(response.body.string()).isEqualTo("greetings")
    }
  }

  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun sayHello() = "hello"
  }

  interface GreetingAction : WebAction {
    @Get("/greet")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun greet(): String
  }

  class RealGreetingAction @Inject constructor() : GreetingAction {
    override fun greet() = "greetings"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
      install(WebActionModule.create<RealGreetingAction>())
    }
  }

  // NB: The server doesn't get a port until after it starts so we create the client module
  // _after_ we start the services
  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(HttpClientModule("default"))
    }

    @Provides
    @Singleton
    fun provideHttpClientsConfig(): HttpClientsConfig {
      return HttpClientsConfig(
        endpoints = mapOf(
          "default" to HttpClientEndpointConfig(
            url = "http://example.com/",
          )
        )
      )
    }
  }
}
