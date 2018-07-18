package misk.web.resources

import misk.asAction
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.config.ConfigModule
import misk.inject.KAbstractModule
import misk.moshi.MoshiModule
import misk.resources.ResourceLoaderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Request
import misk.web.WebProxyInterceptorModule
import misk.web.readUtf8
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.full.functions
import kotlin.test.assertFailsWith

@MiskTest
class WebProxyInterceptorTest {
  @Inject lateinit var interceptorFactoryProvider: Provider<WebProxyInterceptor.Factory>
  @Inject lateinit var mappingBindingsProvider: Provider<List<WebProxyInterceptor.Mapping>>

  private lateinit var interceptor: WebProxyInterceptor
  private val upstreamServer = MockWebServer()

  @MiskTestModule
  val module = TestModule(upstreamServer)

  @BeforeEach
  internal fun setUp() {
    upstreamServer.start()
    val hello = WebProxyInterceptorTest::class.functions.iterator().next()
    interceptor = interceptorFactoryProvider.get().create(hello.asAction()) as WebProxyInterceptor
  }

  fun theFunction(): String {
    return "hello"
  }

  @AfterEach
  internal fun tearDown() {
    upstreamServer.shutdown()
  }

  class TestModule(val upstreamServer: MockWebServer) : KAbstractModule() {
    override fun configure() {
      install(MoshiModule())
      install(ResourceLoaderModule())
      install(WebProxyInterceptorModule())
      install(ConfigModule.create<HttpClientsConfig>("http_clients", HttpClientsConfig(
          endpoints = mapOf(
              "web_proxy_interceptor" to HttpClientEndpointConfig("http://localhost")
          ))))

      multibind<WebProxyInterceptor.Mapping>().toProvider(object: Provider<WebProxyInterceptor.Mapping> {
        override fun get(): WebProxyInterceptor.Mapping {
          return WebProxyInterceptor.Mapping(
              "/local/prefix/",
              upstreamServer.url("/")
          )
        }
      })
    }
  }

  @Test
  internal fun testBindings() {
    val mappingBindings = mappingBindingsProvider.get()
    println(mappingBindings.first().toString())
    assertThat(mappingBindings.size == 1)
  }

  @Test
  fun mappingLocalWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyInterceptor.Mapping("local/prefix/",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun mappingLocalWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyInterceptor.Mapping("/local/prefix",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun mappingToApiPrefix() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyInterceptor.Mapping("/api/test/prefix/",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun mappingUpstreamUrlToFile() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyInterceptor.Mapping("/local/prefix/",
          upstreamServer.url("/test.js"))
    }
  }

  @Test
  internal fun mappingUpstreamUrlWithPathSegments() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyInterceptor.Mapping("/local/prefix/",
          upstreamServer.url("/upstream/prefix/"))
    }
  }

  @Test
  internal fun requestForwardedToUpstreamServerIfPathMatchesWithTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val request = Request(
        HttpUrl.parse("http://localpost/local/prefix/tacos/")!!,
        body = Buffer()
    )

    val response = interceptor.intercept(
        ResourceInterceptorCommon.FakeNetworkChain(request))

    assertThat(response.statusCode).isEqualTo(418)
    assertThat(response.headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
    assertThat(response.readUtf8()).isEqualTo("I am an intercepted response!")

    val recordedRequest = upstreamServer.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/local/prefix/tacos/")
  }

  @Test
  internal fun requestForwardedToUpstreamServerIfPathMatchesWithoutTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val request = Request(
        HttpUrl.parse("http://localpost/local/prefix/tacos")!!,
        body = Buffer()
    )

    val response = interceptor.intercept(
        ResourceInterceptorCommon.FakeNetworkChain(request))

    assertThat(response.statusCode).isEqualTo(418)
    assertThat(response.headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
    assertThat(response.readUtf8()).isEqualTo("I am an intercepted response!")

    val recordedRequest = upstreamServer.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/local/prefix/tacos")
  }

  @Test
  internal fun requestNotForwardedToUpstreamServerIfPathDoesNotMatch() {
    val fakeRequest = Request(
        HttpUrl.parse("http://local/notredirectedprefix/tacos")!!,
        body = Buffer()
    )

    val response = interceptor.intercept(ResourceInterceptorCommon.FakeNetworkChain(fakeRequest))
    assertThat(response.readUtf8()).isEqualTo("I am not intercepted")

    assertThat(upstreamServer.requestCount).isZero()
  }

  @Test
  internal fun returnsServerNotReachable() {
    val urlThatFailed = upstreamServer.url("/local/prefix/tacos")
    upstreamServer.shutdown()

    val request = Request(
        HttpUrl.parse("http://localpost/local/prefix/tacos")!!,
        body = Buffer()
    )

    val response = interceptor.intercept(
        ResourceInterceptorCommon.FakeNetworkChain(request))

    assertThat(response.readUtf8()).isEqualTo("Failed to fetch upstream URL $urlThatFailed")
    assertThat(response.statusCode).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE)
    assertThat(response.headers["Content-Type"]).isEqualTo("text/plain; charset=utf-8")
  }

}