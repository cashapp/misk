package misk.web.actions

import com.google.inject.Guice
import com.google.inject.Provides
import com.squareup.protos.test.grpc.GreeterClient
import com.squareup.protos.test.grpc.GrpcGreeterClient
import misk.MiskTestingServiceModule
import misk.client.GrpcClientModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
class GrpcBindingActionTest {

  @MiskTestModule
  val module = TestWebActionModule()

  @Inject private lateinit var jetty: JettyService

  private lateinit var grpcClient: GreeterClient

  @BeforeEach
  fun beforeEach() {
    val clientInjector = Guice.createInjector(
      ClientModule(
        jetty
      )
    )
    grpcClient = clientInjector.getInstance()
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(GrpcClientModule.create<GreeterClient, GrpcGreeterClient>("greeter"))
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
        endpoints = mapOf(
          "greeter" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
        )
      )
    }
  }

  @Test
  fun `allows actions with empty requests`() {
    val response = grpcClient.Greet().executeBlocking(Unit)
    assertThat(response.message).isEqualTo("Hola")
  }
}
