package misk.client

import com.google.inject.Provides
import com.google.inject.name.Names
import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
import com.squareup.protos.test.parsing.Robot
import com.squareup.protos.test.parsing.Warehouse
import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcMethod
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import misk.inject.KAbstractModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingDeque
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@MiskTest(startService = true)
internal class GrpcClientProviderTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @field:Named("robots") lateinit var httpClientProvider: Provider<OkHttpClient>
  @Inject lateinit var httpClientsConfigProvider: Provider<HttpClientsConfig>
  @Inject lateinit var httpClientConfigUrlProvider: HttpClientConfigUrlProvider

  val log = LinkedBlockingDeque<String>()

  @Test
  fun happyPath() {
    val robotLocator = newClient(listOf(SimpleInterceptorFactory()))

    assertThat(log).containsExactlyInAnyOrder(
        "create Locate[${Robot::class.qualifiedName}]: ${Warehouse::class.qualifiedName}",
        "create SayHello[${HelloRequest::class.qualifiedName}]: ${HelloReply::class.qualifiedName}"
    )
    log.clear()

    val request1 = HelloRequest.Builder()
        .name("r2d2")
        .build()
    assertThat(robotLocator.SayHello().executeBlocking(request1)).isEqualTo(HelloReply.Builder()
        .message("boop r2d2")
        .build())
    assertThat(log).containsExactly(
        ">> SayHello /RobotLocator/SayHello",
        "<< SayHello /RobotLocator/SayHello 200"
    )
    log.clear()

    val request2 = Robot.Builder()
        .robot_id(3)
        .robot_token("c3po")
        .build()
    assertThat(robotLocator.Locate().executeBlocking(request2)).isEqualTo(Warehouse.Builder()
        .warehouse_id(100L)
        .robots(mapOf(3 to request2))
        .build())
    assertThat(log).containsExactly(
        ">> Locate /RobotLocator/Locate",
        "<< Locate /RobotLocator/Locate 200"
    )
  }

  @Test
  fun proxyToString() {
    val robotLocator = newClient(listOf(SimpleInterceptorFactory()))
    assertThat(robotLocator.toString()).isEqualTo("GrpcClient:${RobotLocator::class.qualifiedName}")
  }

  private fun newClient(interceptorFactories: List<SimpleInterceptorFactory>): RobotLocator {
    val grpcClientProvider = GrpcClientProvider(
        kclass = RobotLocator::class,
        grpcClientClass = GrpcRobotLocator::class,
        name = "robots",
        httpClientProvider = httpClientProvider
    )

    return grpcClientProvider.get(
        baseUrl = httpClientConfigUrlProvider.getUrl(httpClientsConfigProvider.get()["robots"]),
        httpClient = httpClientProvider.get(),
        interceptorFactories = interceptorFactories
    )
  }

  inner class SimpleInterceptorFactory : ClientNetworkInterceptor.Factory {
    override fun create(action: ClientAction): ClientNetworkInterceptor? {
      log += "create ${action.name}${action.parameterTypes}: ${action.returnType}"
      return SimpleInterceptor(action)
    }
  }

  inner class SimpleInterceptor(val action: ClientAction) : ClientNetworkInterceptor {
    override fun intercept(chain: ClientNetworkChain): Response {
      require(chain.action == action)
      log += ">> ${chain.action.name} ${chain.request.url.encodedPath}"
      val response = chain.proceed(chain.request)
      log += "<< ${chain.action.name} ${chain.request.url.encodedPath} ${response.code}"
      return response
    }
  }

  interface RobotLocator : Service {
    fun Locate(): GrpcCall<Robot, Warehouse>
    fun SayHello(): GrpcCall<HelloRequest, HelloReply>
  }

  class GrpcRobotLocator(
    private val client: GrpcClient
  ) : RobotLocator {
    override fun Locate(): GrpcCall<Robot, Warehouse> = client.newCall(GrpcMethod(
        path = "/RobotLocator/Locate",
        requestAdapter = Robot.ADAPTER,
        responseAdapter = Warehouse.ADAPTER
    ))

    override fun SayHello(): GrpcCall<HelloRequest, HelloReply> = client.newCall(GrpcMethod(
        path = "/RobotLocator/SayHello",
        requestAdapter = HelloRequest.ADAPTER,
        responseAdapter = HelloReply.ADAPTER
    ))
  }

  interface RobotLocatorLocateBlockingServer : Service {
    @WireRpc(
        path = "/RobotLocator/Locate",
        requestAdapter = "com.squareup.protos.test.parsing.Robot#ADAPTER",
        responseAdapter = "com.squareup.protos.test.parsing.Warehouse#ADAPTER"
    )
    fun Locate(request: Robot): Warehouse
  }

  interface RobotLocatorSayHelloBlockingServer : Service {
    @WireRpc(
        path = "/RobotLocator/SayHello",
        requestAdapter = "com.squareup.protos.test.grpc.HelloRequest#ADAPTER",
        responseAdapter = "com.squareup.protos.test.grpc.HelloReply#ADAPTER"
    )
    fun SayHello(request: HelloRequest): HelloReply
  }

  class LocateGrpcAction @Inject constructor() : RobotLocatorLocateBlockingServer, WebAction {
    override fun Locate(request: Robot): Warehouse {
      return Warehouse.Builder()
          .warehouse_id(100L)
          .robots(mapOf(request.robot_id to request))
          .build()
    }
  }

  class SayHelloGrpcAction @Inject constructor() : RobotLocatorSayHelloBlockingServer, WebAction {
    override fun SayHello(request: HelloRequest): HelloReply {
      return HelloReply.Builder()
          .message("boop ${request.name}")
          .build()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(HttpClientModule("robots", Names.named("robots")))
      install(WebTestingModule(webConfig = WebTestingModule.TESTING_WEB_CONFIG.copy(http2 = true)))
      install(WebActionModule.create<LocateGrpcAction>())
      install(WebActionModule.create<SayHelloGrpcAction>())
    }

    @Provides
    @Singleton
    fun provideHttpClientsConfig(jetty: JettyService): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "robots" to HttpClientEndpointConfig(
                  url = jetty.httpsServerUrl.toString(),
                  clientConfig = HttpClientConfig(
                      ssl = HttpClientSSLConfig(
                          cert_store = null,
                          trust_store = TrustStoreConfig(
                              resource = "classpath:/ssl/server_cert.pem",
                              format = SslLoader.FORMAT_PEM
                          )
                      )
                  )
              )
          )
      )
    }
  }
}
