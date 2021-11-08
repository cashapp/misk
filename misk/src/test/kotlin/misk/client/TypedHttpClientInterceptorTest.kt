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
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import okhttp3.Interceptor
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

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
    assertThat(response.body()?.name!!).isEqualTo("supertrex")
    assertThat(response.headers()["X-Original-From"]).isEqualTo("dinosaur.getDinosaur")
    assertThat(response.headers()["X-Application-Action-Name"]).isEqualTo("dinosaur.getDinosaur")
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

  class ClientActionHeaderInterceptor(val action: ClientAction) : Interceptor {
    override fun intercept(chain: Interceptor.Chain) =
      chain.proceed(chain.request()).newBuilder()
        .addHeader("X-Application-Action-Name", action.name)
        .build()

    class Factory: ClientApplicationInterceptorFactory {
      override fun create(action: ClientAction) = ClientActionHeaderInterceptor(action)
    }
  }

  /** [ClientNetworkInterceptor] that adds the action name as a header */
  class ClientHeaderInterceptor(private val name: String) : ClientNetworkInterceptor {
    override fun intercept(chain: ClientNetworkChain): Response =
      chain.proceed(
        chain.request.newBuilder()
          .addHeader("X-From", name)
          .build()
      )

    class Factory : ClientNetworkInterceptor.Factory {
      override fun create(action: ClientAction) = ClientHeaderInterceptor(action.name)
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(WebServerTestingModule())
      install(WebActionModule.create<ReturnADinosaurAction>())
      multibind<NetworkInterceptor.Factory>().to<ServerHeaderInterceptor.Factory>()
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DinoClientModule(jetty))
      multibind<ClientNetworkInterceptor.Factory>().to<ClientHeaderInterceptor.Factory>()
      multibind<ClientApplicationInterceptorFactory>().to<ClientActionHeaderInterceptor.Factory>()
    }
  }
}
