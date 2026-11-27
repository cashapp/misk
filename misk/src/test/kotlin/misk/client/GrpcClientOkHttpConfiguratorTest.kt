package misk.client

import com.google.inject.Guice
import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GrpcClientOkHttpConfiguratorTest {
  @Test
  fun `per-method configurator installs listener and preserves existing application interceptor`() {
    MockWebServer().use { server ->
      server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
      server.start()
      val reply = HelloReply.Builder().message("configured reply").build()
      val encoded = HelloReply.ADAPTER.encode(reply)
      server.enqueue(
        MockResponse()
          .setHeader("Content-Type", "application/grpc")
          .setHeader("grpc-status", "0")
          .setBody(Buffer().writeByte(0).writeInt(encoded.size).write(encoded))
      )
      val starts = AtomicInteger()
      val injector =
        Guice.createInjector(
          object : KAbstractModule() {
            override fun configure() {
              install(MiskTestingServiceModule())
              install(
                GrpcClientModule.create<
                  GrpcClientProviderTest.MisconfiguredService,
                  GrpcClientProviderTest.GrpcMisconfiguredService,
                >(
                  "configured"
                )
              )
              bind<HttpClientsConfig>()
                .toInstance(
                  HttpClientsConfig(
                    endpoints = mapOf("configured" to HttpClientEndpointConfig(server.url("/").toString()))
                  )
                )
              multibind<ClientApplicationInterceptorFactory>()
                .toInstance(
                  object : ClientApplicationInterceptorFactory {
                    override fun create(action: ClientAction) = Interceptor { chain ->
                      chain.proceed(chain.request().newBuilder().header("X-Existing", "preserved").build())
                    }
                  }
                )
              multibind<ClientOkHttpConfigurator>()
                .toInstance(
                  object : ClientOkHttpConfigurator {
                    override fun configure(action: ClientAction, builder: OkHttpClient.Builder) {
                      val existing = builder.build().eventListenerFactory
                      builder.eventListenerFactory { call ->
                        existing.create(call) +
                          object : EventListener() {
                            override fun callStart(call: Call) {
                              starts.incrementAndGet()
                            }
                          }
                      }
                      builder.addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("X-Configured-Action", action.name).build())
                      }
                    }
                  }
                )
            }
          }
        )
      val api = injector.getInstance<GrpcClientProviderTest.MisconfiguredService>()
      assertThat(api.SayHello().executeBlocking(HelloRequest.Builder().name("distinct request").build()))
        .isEqualTo(reply)
      val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
      assertThat(sent.getHeader("X-Existing")).isEqualTo("preserved")
      assertThat(sent.getHeader("X-Configured-Action")).isEqualTo("configured.SayHello")
      assertThat(starts.get()).isEqualTo(1)
    }
  }
}
