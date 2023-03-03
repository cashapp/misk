package misk.client

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.name.Named
import com.google.inject.name.Names
import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcMethod
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import helpers.protos.Dinosaur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.scope.ActionScoped
import misk.scope.ActionScopedProviderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.HttpCall
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class PropagatingScopeActionInInterceptorsTest {

  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var scope: ActionScope

  private lateinit var client: misk.client.ReturnADinosaur
  private lateinit var suspendClient: TypedHttpClientTest.ReturnADinosaurNonBlocking
  private lateinit var dinoService: DinoService

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    client = clientInjector.getInstance()
    suspendClient = clientInjector.getInstance(
      Names.named("nonblockingDinosaur")
    )
    dinoService = clientInjector.getInstance()
  }

  @Test
  fun happyPath() {
    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("language-preference")) to "es-US"
    )
    val response = scope.enter(seedData).use {
      client.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
    }
    assertThat(response.body()!!.name).isEqualTo("es-US")
  }

  @Test
  fun coroutines() {
    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("language-preference")) to "es-US"
    )
    val response = scope.enter(seedData).use {

      runBlocking(Dispatchers.IO + it.asContextElement()) {
        client.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
      }
    }
    assertThat(response.body()!!.name).isEqualTo("es-US")
  }

  @Test
  fun grpc() {
    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("language-preference")) to "es-US"
    )
    val response = scope.enter(seedData).use {
      dinoService.GetDinosour().executeBlocking(Dinosaur.Builder().name("trex").build())
    }
    assertThat(response.name).isEqualTo("es-US")
  }

  @Test
  fun grpcSuspended() {
    val seedData: Map<Key<*>, Any> = mapOf(
      keyOf<String>(Names.named("language-preference")) to "es-US"
    )
    val response = scope.enter(seedData).use {
      runBlocking(Dispatchers.IO +  it.asContextElement()) {
        dinoService.GetDinosour().execute(Dinosaur.Builder().name("trex").build())
      }
    }
    assertThat(response.name).isEqualTo("es-US")
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DinoClientModule(jetty))
      install(TypedHttpClientModule.create<TypedHttpClientTest.ReturnADinosaurNonBlocking>(
        "dinosaur",
        Names.named("nonblockingDinosaur")
      ))
      multibind<CallFactoryWrapper>().to<LanguageCallFactoryWrapper>()
      multibind<ClientApplicationInterceptorFactory>().to<ClientScopeActionHeaderInterceptor.Factory>()

      install(GrpcClientModule.create<DinoService, GrpcDinoService>("dinosaur"))

      install(object : ActionScopedProviderModule() {
        override fun configureProviders() {
          bindSeedData(String::class, Names.named("language-preference"))
        }
      })
    }
  }

  class LanguageCallFactoryWrapper @Inject constructor(
    val actionScope: ActionScope
  ) : CallFactoryWrapper {
    override fun wrap(action: ClientAction, delegate: Call.Factory): Call.Factory? {
       return Call.Factory { request ->
          if (!actionScope.inScope()) {
            delegate.newCall(request)
          }

         val language = actionScope.get(Key.get(String::class.java, Names.named("language-preference")))
         val newRequest = request.newBuilder().tag(LanguageContainer(language)).build()
         delegate.newCall(newRequest)
       }
    }
  }

  data class LanguageContainer(val language: String)

  class ClientScopeActionHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
      val language = chain.request().tag() as? LanguageContainer
      return chain.proceed(
        chain.request().newBuilder()
          .addHeader("Content-Language", language?.language ?: "en-US")
          .build()
      )
    }

    class Factory @Inject constructor(
      @Named("language-preference") private val languagePreference: ActionScoped<String>
    ): ClientApplicationInterceptorFactory {
      override fun create(action: ClientAction) = ClientScopeActionHeaderInterceptor()
    }
  }

  class ReturnADinosaur @Inject constructor(
    val actionScope: ActionScoped<HttpCall>,
  ) : WebAction, ReturnADinosaurBlockingServer {
    @Post("/cooldinos")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getDinosaur(@RequestBody requestBody: Dinosaur, @RequestHeaders headers: Headers):
      Dinosaur = requestBody.newBuilder().name(headers["Content-Language"]).build()

    override fun GetDinosour(request: Dinosaur): Dinosaur {
      return request.newBuilder().name(actionScope.get().requestHeaders["Content-Language"]).build()
    }
  }

  interface ReturnADinosaurBlockingServer : Service {
    @WireRpc(
      path = "/DinoService/GetDinosaur",
      requestAdapter = "helpers.protos.Dinosaur#ADAPTER",
      responseAdapter = "helpers.protos.Dinosaur#ADAPTER"
    )
    fun GetDinosour(request: Dinosaur): Dinosaur
  }

  class GrpcDinoService(
    private val client: GrpcClient
  ) : DinoService {
    override fun GetDinosour(): GrpcCall<Dinosaur, Dinosaur> = client.newCall(
      GrpcMethod(
        path = "/DinoService/GetDinosaur",
        requestAdapter = Dinosaur.ADAPTER,
        responseAdapter = Dinosaur.ADAPTER
      )
    )
  }

  interface DinoService : Service {
    fun GetDinosour(): GrpcCall<Dinosaur, Dinosaur>
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(WebServerTestingModule())
      install(WebActionModule.create<ReturnADinosaur>())
    }
  }
}
