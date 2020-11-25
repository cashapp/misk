package misk.fastpath

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provides
import com.google.inject.name.Names
import javax.inject.Inject
import javax.inject.Singleton
import misk.MiskTestingServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Fastpath Misk todos
 *
 * 1. Move ALL the code to com.squareup.fastpath.misk in evently repo
 *     evently/
 *       events-fastpath-protos
 *       events-fastpath          (service container)
 *       events-fastpath-misk
 *
 * 2. ActionScoped Fastpath interface + implementation to replace ClientFastpath, ServerFastpath
 *
 * 3. Headers + protos consistent with the fastpath code in Evently's events-fastpath module
 */
@MiskTest(startService = true)
internal class FastpathTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService

  private lateinit var clientInjector: Injector

  @BeforeEach
  fun createClient() {
    clientInjector = Guice.createInjector(ClientModule(jetty))
  }

  @Test
  fun happyPath() {
    val client: OkHttpClient = clientInjector.getInstance(Names.named("sample"))
    val fastpath = clientInjector.getInstance(ClientFastpath::class.java)

    fastpath.collecting = true

    val call = client.newCall(Request.Builder()
        .url(jetty.httpServerUrl.resolve("/echo")!!)
        .post("hello keyar".toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE))
        .build())
    val response = call.execute()
    assertThat(response.body?.string()).isEqualTo("1 event emitted")

    assertThat(fastpath.events).containsExactly("echo event hello keyar")
  }

  @Test
  fun fastpathNotCollecting() {
    val client: OkHttpClient = clientInjector.getInstance(Names.named("sample"))
    val fastpath = clientInjector.getInstance(ClientFastpath::class.java)

    fastpath.collecting = false

    val call = client.newCall(Request.Builder()
        .url(jetty.httpServerUrl.resolve("/echo")!!)
        .post("hello keyar".toRequestBody(MediaTypes.TEXT_PLAIN_UTF8_MEDIA_TYPE))
        .build())
    val response = call.execute()
    assertThat(response.body?.string()).isEqualTo("0 events emitted")

    assertThat(fastpath.events).isEmpty()
  }

  class EchoAction @Inject constructor(
    val fastpath: ServerFastpath
  ) : WebAction {
    @Post("/echo")
    @RequestContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun echo(@RequestBody request: String): String {
      if (fastpath.collecting) {
        fastpath.events.add("echo event $request")
        return "1 event emitted"
      } else {
        return "0 events emitted"
      }
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<EchoAction>())
      install(MiskFastpathModule())
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(HttpClientModule("sample", Names.named("sample")))
      install(MiskFastpathModule())
    }

    @Provides
    @Singleton
    fun provideHttpClientConfig(): HttpClientsConfig {
      return HttpClientsConfig(
          endpoints = mapOf(
              "sample" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
          ))
    }
  }
}
