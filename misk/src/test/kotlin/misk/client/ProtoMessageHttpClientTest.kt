package misk.client

import com.google.inject.Guice
import com.google.inject.Provides
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class ProtoMessageHttpClientTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var httpClient: ProtoMessageHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    httpClient = clientInjector.getInstance(Names.named("dinosaur"))
  }

  @Test
  fun protoMessageHttpCall() {
    val dinoMessage = Dinosaur.Builder()
      .name("stegosaurus")
      .picture_urls(
        listOf(
          "https://cdn.dinopics.com/stego.jpg",
          "https://cdn.dinopics.com/stego2.png"
        )
      )
      .build()

    val response = httpClient.post<Dinosaur>("/cooldinos", dinoMessage)
    assertThat(response.name).isEqualTo("supersaurus")
    assertThat(response.picture_urls).isEqualTo(
      listOf(
        "https://cdn.dinopics.com/stego.jpg",
        "https://cdn.dinopics.com/stego2.png"
      )
    )
  }

  class ReturnADinosaur @Inject constructor() : WebAction {
    @Post("/cooldinos")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getDinosaur(@RequestBody requestBody: Dinosaur):
      Dinosaur = requestBody.newBuilder().name("supersaurus").build()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(WebServerTestingModule())
      install(WebActionModule.create<ReturnADinosaur>())
    }
  }

  // NB(mmihic): The server doesn't get a port until after it starts, so we
  // need to create the client module _after_ we start the services
  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(HttpClientModule("dinosaur", Names.named("dinosaur")))
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
        endpoints = mapOf(
          "dinosaur" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
        )
      )
    }
  }
}
