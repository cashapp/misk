package misk.client

import com.google.inject.Guice
import com.google.inject.Provides
import com.google.inject.util.Modules
import helpers.protos.Dinosaur
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.moshi.MoshiModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class TypedHttpClientTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule()
  )

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var client: ReturnADinosaur

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(MoshiModule(), ClientModule(jetty))
    client = clientInjector.getInstance()
  }

  @Test
  fun useTypedClient() {
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

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<ReturnADinosaurAction>())
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(TypedHttpClientModule.create<ReturnADinosaur>("dinosaur"))
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "dinosaur" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
          ))
    }

  }

}
