package misk.web

import ch.qos.logback.classic.Level
import com.google.inject.Guice
import com.google.inject.Provides
import misk.MiskTestingServiceModule
import misk.client.HttpClientConfig
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientSSLConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.logging.LogCollectorModule
import misk.security.ssl.SslLoader
import misk.security.ssl.TrustStoreConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import misk.logging.LogCollector
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.interceptors.RequestLoggingInterceptor

private const val s = "WriteMdc"

@MiskTest(startService = true)
class SingleRequestThreadTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var logCollector: LogCollector

  private lateinit var client: OkHttpClient

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty))
    client = clientInjector.getInstance()
  }

  @AfterEach
  fun checkNoErrorsLogged() {
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR)).isEmpty()
  }

  @Test
  fun mdcIsCleared() {
    // This request will trigger a particular MDC tag to be set
    client.newCall(
      Request.Builder()
        .url(jetty.httpsServerUrl!!.resolve("/hello/${HelloAction.SPECIAL_MESSAGE}")!!)
        .build()
    ).execute().body!!.string()

    // Verify MDC tag survived to the request/response log    
    val contexts = logCollector.takeEvents(RequestLoggingInterceptor::class).single().mdcPropertyMap
    assertThat(contexts).containsEntry(HelloAction.TAG_NAME, HelloAction.TAG_VALUE)
    
    // These requests will not trigger an MDC tag to be set
    // We run multiple requests just to reduce the chance of a false positive (e.g. Jetty swapping
    // which threads are handling the requests, though I don't think this is possible with our config)
    repeat(10) {
      client.newCall(
        Request.Builder()
          .url(jetty.httpsServerUrl!!.resolve("/hello/Evan")!!)
          .build()
      ).execute()

      // Verify MDC tag did not bleed over
      val allContexts = logCollector.takeEvents(RequestLoggingInterceptor::class)
        .map { it.mdcPropertyMap }
      assertThat(allContexts).noneMatch { it.containsKey(HelloAction.TAG_NAME) }
    }
  }

  class HelloAction @Inject constructor() : WebAction {
    @Get("/hello/{message}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    @LogRequestResponse(
      ratePerSecond = 0,
      errorRatePerSecond = 0,
      bodySampling = 1.0,
      errorBodySampling = 1.0
    )
    fun sayHello(@PathParam message: String): String {
      if(message == SPECIAL_MESSAGE) {
        MDC.put(TAG_NAME, TAG_VALUE)
      }
      
      return "hello, $message"
    }
    
    companion object {
      const val SPECIAL_MESSAGE = "WriteMdc"
      const val TAG_NAME = "TagName"
      const val TAG_VALUE = "TagValue"      
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(
        WebServerTestingModule(
          webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(
            // Jetty uses some of these threads for other purposes.
            // This is the smallest number of jetty threads that will result in one request thread.
            jetty_min_thread_pool_size = 6,
            jetty_max_thread_pool_size = 6,
          )
        )
      )
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
    }
  }

  // NB: The server doesn't get a port until after it starts so we create the client module
  // _after_ we start the services
  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(HttpClientModule("default"))
    }

    @Provides
    @Singleton
    fun provideHttpClientsConfig(): HttpClientsConfig {
      return HttpClientsConfig(
        endpoints = mapOf(
          "default" to HttpClientEndpointConfig(
            url = "http://example.com/",
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
