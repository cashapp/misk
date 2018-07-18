package misk.client

import com.google.inject.Guice
import com.google.inject.Provides
import helpers.protos.Dinosaur
import misk.Action
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.moshi.MoshiModule
import misk.resources.ResourceLoaderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionEntry
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class TypedHttpClientInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  private lateinit var client: ReturnADinosaur

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    client = clientInjector.getInstance()
  }

  @Test
  fun useTypedClient() {
    val response = client.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()).isNotNull()
    assertThat(response.body()?.name!!)
        .isEqualTo("supertrex from dinosaur.getDinosaur intercepted on response")
    assertThat(response.headers()["X-Original-From"]).isEqualTo("dinosaur.getDinosaur")
  }

  interface ReturnADinosaur {
    @POST("/cooldinos")
    fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
  }

  class ReturnADinosaurAction : WebAction {
    @Post("/cooldinos")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getDinosaur(@RequestBody request: Dinosaur): Dinosaur = request.newBuilder()
        .name("super${request.name}")
        .build()
  }

  /** Server [NetworkInterceptor] that echos back the X-Originating-Action from the request */
  class ServerHeaderInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain): misk.web.Response<*> {
      val originatingAction = chain.request.headers["X-From"]
      val response = chain.proceed(chain.request)
      return if (originatingAction != null) {
        val newHeaders = response.headers.newBuilder()
            .add("X-Original-From", originatingAction)
            .build()
        misk.web.Response(response.body, newHeaders, response.statusCode)
      } else response
    }

    class Factory : NetworkInterceptor.Factory {
      override fun create(action: Action): NetworkInterceptor? = ServerHeaderInterceptor()
    }
  }

  /** [ClientNetworkInterceptor] that adds the action name as a header */
  class ClientHeaderInterceptor(private val name: String) : ClientNetworkInterceptor {
    override fun intercept(chain: ClientNetworkChain): Response =
        chain.proceed(chain.request.newBuilder()
            .addHeader("X-From", name)
            .build())

    class Factory : ClientNetworkInterceptor.Factory {
      override fun create(action: ClientAction) = ClientHeaderInterceptor(action.name)
    }
  }

  /** [ClientApplicationInterceptor] that modifies the request to include the action name */
  class ClientNameInterceptor(private val name: String) : ClientApplicationInterceptor {
    override fun interceptBeginCall(chain: BeginClientCallChain): Call<Any> {
      val dinosaur = chain.args[0] as? Dinosaur
      return if (dinosaur != null) {
        val newDinosaur = dinosaur.newBuilder()
            .name("${dinosaur.name} from $name")
            .build()
        chain.proceed(listOf(newDinosaur))
      } else chain.proceed(chain.args)
    }

    override fun intercept(chain: ClientChain) {
      chain.proceed(chain.args, object : Callback<Any> {
        override fun onFailure(call: Call<Any>, t: Throwable) {
          chain.callback.onFailure(call, t)
        }

        override fun onResponse(call: Call<Any>, response: retrofit2.Response<Any>) {
          val dinosaur = response.body() as? Dinosaur
          val updatedResponse = if (dinosaur != null) {
            val newDinosaur = dinosaur.newBuilder()
                .name("${dinosaur.name} intercepted on response")
                .build()
            @Suppress("UNCHECKED_CAST")
            retrofit2.Response.success(newDinosaur, response.headers()) as retrofit2.Response<Any>
          } else response

          chain.callback.onResponse(call, updatedResponse)
        }
      })
    }

    class Factory : ClientApplicationInterceptor.Factory {
      override fun create(action: ClientAction) = ClientNameInterceptor(action.name)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry(ReturnADinosaurAction::class))
      multibind<NetworkInterceptor.Factory>().to<ServerHeaderInterceptor.Factory>()
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MoshiModule())
      install(ResourceLoaderModule())
      install(TypedHttpClientModule.create<ReturnADinosaur>("dinosaur"))
      multibind<ClientNetworkInterceptor.Factory>().to<ClientHeaderInterceptor.Factory>()
      multibind<ClientApplicationInterceptor.Factory>().to<ClientNameInterceptor.Factory>()
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