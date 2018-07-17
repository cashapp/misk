package misk.client

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provides
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.moshi.MoshiModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionEntry
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class TypedHttpClientTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var clientInjector: Injector

  @BeforeEach
  fun createClient() {
    clientInjector = Guice.createInjector(MoshiModule(), ClientModule(jetty))
  }

  @Test
  fun useTypedClient() {
    val client: ReturnADinosaur = clientInjector.getInstance(Names.named("dinosaur"))
    val response = client.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()).isNotNull()
    assertThat(response.body()?.name!!).isEqualTo("supertrex")
  }

  @Test
  fun useTypedClientWithWire() {
    val client: ReturnAProtoDinosaur = clientInjector.getInstance(Names.named("protoDino"))
    val response = client.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()).isNotNull()
    assertThat(response.body()?.name!!).isEqualTo("supertrex")
  }

  interface ReturnADinosaur {
    @POST("/cooldinos")
    fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
  }

  class ReturnADinosaurAction : WebAction {
    @Post("/cooldinos")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getDinosaur(@RequestBody request: Dinosaur):
        Dinosaur = request.newBuilder().name("super${request.name}").build()
  }

  interface ReturnAProtoDinosaur {
    @POST("/protodinos")
    @Headers(
        "Accept: " + MediaTypes.APPLICATION_PROTOBUF,
        "Content-type: " + MediaTypes.APPLICATION_PROTOBUF)
    fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
  }

  class ReturnAProtoDinosaurAction : WebAction {
    @Post("/protodinos")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun getDinosaur(@RequestBody request: Dinosaur):
        Dinosaur = request.newBuilder().name("super${request.name}").build()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry(ReturnADinosaurAction::class))
      multibind<WebActionEntry>().toInstance(WebActionEntry(ReturnAProtoDinosaurAction::class))
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(TypedHttpClientModule.create<ReturnADinosaur>("dinosaur", Names.named("dinosaur")))
      install(
          TypedHttpClientModule.create<ReturnAProtoDinosaur>("protoDino", Names.named("protoDino")))
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "dinosaur" to HttpClientEndpointConfig(jetty.httpServerUrl.toString()),
              "protoDino" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
          ))
    }
  }
}
