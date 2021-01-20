package misk.client

import com.google.inject.Guice
import helpers.protos.Dinosaur
import misk.Action
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.Callback
import javax.inject.Inject

@MiskTest
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

  /** Server [NetworkInterceptor] that echos back the X-Originating-Action from the request. */
  class ServerHeaderInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      val originatingAction = chain.httpCall.requestHeaders["X-From"]
      if (originatingAction != null) {
        chain.httpCall.setResponseHeader("X-Original-From", originatingAction)
      }
      chain.proceed(chain.httpCall)
    }

    class Factory @Inject constructor() : NetworkInterceptor.Factory {
      override fun create(action: Action) = ServerHeaderInterceptor()
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
      install(WebActionModule.create<ReturnADinosaurAction>())
      multibind<NetworkInterceptor.Factory>().to<ServerHeaderInterceptor.Factory>()
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DinoClientModule(jetty))
      multibind<ClientNetworkInterceptor.Factory>().to<ClientHeaderInterceptor.Factory>()
      multibind<ClientApplicationInterceptor.Factory>().to<ClientNameInterceptor.Factory>()
    }
  }
}
