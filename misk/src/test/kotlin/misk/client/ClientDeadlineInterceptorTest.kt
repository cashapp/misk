package misk.client

import com.google.inject.Provides
import com.google.inject.name.Named
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import misk.ActionDeadline
import misk.MiskTestingServiceModule
import misk.exceptions.ActionException
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest
class ClientDeadlineInterceptorTest {

  @MiskTestModule
  val module = TestModule()

  @Named("dinos") @Inject private lateinit var client: DinosaurService
  @Inject private lateinit var mockWebServer: MockWebServer
  @Inject private lateinit var clock: FakeClock

  @Inject private lateinit var actionDeadline: ActionScoped<ActionDeadline>

  @BeforeEach
  fun before() {
    mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
  }

  @Test fun `no deadline`() {
    assertThat(client.postDinosaur(Dinosaur.Builder().build()).execute().code()).isEqualTo(200)
  }

  @Test fun `within deadline`() {
    val timeout = Duration.ofMillis(5)
    actionDeadline.get().overriding(timeout) {
      assertThat(client.postDinosaur(Dinosaur.Builder().build()).execute().code()).isEqualTo(200)
    }
  }

  @Test fun `at deadline`() {
    val timeout = Duration.ofMillis(5)

    actionDeadline.get().overriding(timeout) {
      clock.add(timeout)

      assertThrows<ActionException> {
        client.postDinosaur(Dinosaur.Builder().build()).execute()
      }
    }
  }

  @Test fun `after deadline`() {
    val timeout = Duration.ofMillis(5)
    actionDeadline.get().overriding(timeout) {
      clock.add(timeout.plusMillis(1))

      assertThrows<ActionException> {
        client.postDinosaur(Dinosaur.Builder().build()).execute()
      }
    }
  }

  class TestModule : KAbstractModule() {

    companion object {
      @Volatile var deadline: Instant? = null
    }

    override fun configure() {
      install(TypedHttpClientModule.create<DinosaurService>("dinos", Names.named("dinos")))
      multibind<ClientApplicationInterceptor.Factory>().to<ClientDeadlineAppInterceptor.Factory>()

      install(MiskTestingServiceModule())
      bind<MockWebServer>().toInstance(MockWebServer())
    }

    @Provides @Singleton
    fun deadline(clock: Clock): ActionScoped<ActionDeadline> {
      val deadline = ActionDeadline(clock = clock, deadline = null)
      return object : ActionScoped<ActionDeadline> {
        override fun get(): ActionDeadline = deadline
      }
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(server: MockWebServer): HttpClientsConfig {
      val url = server.url("/")
      return HttpClientsConfig(
          endpoints = mapOf("dinos" to HttpClientEndpointConfig(
              url = url.toString(),
              readTimeout = Duration.ofMillis(100)
          )))
    }
  }

  interface DinosaurService {
    @POST("/cooldinos") fun postDinosaur(@Body request: Dinosaur): Call<Void>
  }
}
