package misk.client

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provides
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
internal class TypedHttpClientTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService

  private lateinit var clientInjector: Injector

  @BeforeEach
  fun createClient() {
    clientInjector = Guice.createInjector(ClientModule(jetty))
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

  @Test
  fun buildDynamicClients() {
    val typedClientFactory = clientInjector.getInstance(TypedClientFactory::class.java)

    val dinoClient = typedClientFactory.build<ReturnADinosaur>(
      HttpClientEndpointConfig(jetty.httpServerUrl.toString()),
      "dynamicDino"
    )
    val response = dinoClient.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()).isNotNull()
    assertThat(response.body()?.name!!).isEqualTo("supertrex")

    val protoDinoClient = typedClientFactory.build<ReturnAProtoDinosaur>(
      HttpClientEndpointConfig(jetty.httpServerUrl.toString()),
      "dynamicProtoDino"
    )
    val protoResponse = protoDinoClient.getDinosaur(
      Dinosaur.Builder().name("trex").build()
    ).execute()
    assertThat(protoResponse.code()).isEqualTo(200)
    assertThat(protoResponse.body()).isNotNull()
    assertThat(protoResponse.body()?.name!!).isEqualTo("supertrex")
  }

  @Test
  fun mustBeRetrofitInterface() {
    val typedClientFactory = clientInjector.getInstance(TypedClientFactory::class.java)

    val exception = assertFailsWith<IllegalArgumentException> {
      typedClientFactory.build<NotARetrofitInterface>(
        HttpClientEndpointConfig(jetty.httpServerUrl.toString()),
        "notARetrofitInterface"
      )
    }
    assertThat(exception).hasMessage(
      "${NotARetrofitInterface::class} is not a Retrofit interface (no @POST or @GET methods)"
    )
  }

  @Test
  fun suspendingMethod() {
    val client: ReturnADinosaurNonBlocking = clientInjector.getInstance(
      Names.named("nonblockingDinosaur")
    )
    val response = runBlocking {
      client.getDinosaur(Dinosaur.Builder().name("trex").build())
    }
    assertThat(response.name!!).isEqualTo("supertrex")
  }

  interface ReturnADinosaur {
    @POST("/cooldinos")
    fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
  }

  interface ReturnADinosaurNonBlocking {
    @POST("/cooldinos")
    suspend fun getDinosaur(@Body request: Dinosaur): Dinosaur
  }

  class ReturnADinosaurAction @Inject constructor() : WebAction {
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
      "Content-type: " + MediaTypes.APPLICATION_PROTOBUF
    )
    fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
  }

  class ReturnAProtoDinosaurAction @Inject constructor() : WebAction {
    @Post("/protodinos")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun getDinosaur(@RequestBody request: Dinosaur): Dinosaur =
      request.newBuilder().name("super${request.name}").build()
  }

  interface NotARetrofitInterface {
    fun getDinosaur(request: Dinosaur): Call<Dinosaur>
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(WebServerTestingModule())
      install(WebActionModule.create<ReturnADinosaurAction>())
      install(WebActionModule.create<ReturnAProtoDinosaurAction>())
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(TypedHttpClientModule.create<ReturnADinosaur>("dinosaur", Names.named("dinosaur")))
      install(TypedHttpClientModule.create<ReturnADinosaurNonBlocking>(
        "dinosaur",
        Names.named("nonblockingDinosaur")
      ))
      install(
        TypedHttpClientModule.create<ReturnAProtoDinosaur>("protoDino", Names.named("protoDino"))
      )
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
        endpoints = mapOf(
          "dinosaur" to HttpClientEndpointConfig(jetty.httpServerUrl.toString()),
          "protoDino" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
        )
      )
    }
  }
}
