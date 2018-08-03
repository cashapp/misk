package misk.web.actions

import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Request
import misk.web.WebActionEntry
import misk.web.WebProxyActionModule
import misk.web.WebTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import misk.web.resources.ResourceInterceptorCommon
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
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
import javax.inject.Named
import javax.inject.Provider
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class WebProxyActionTest {
  private val upstreamServer = MockWebServer()

  @MiskTestModule
  val module = TestModule(upstreamServer)

  @Inject @Named("web_proxy_action") private lateinit var httpClient: OkHttpClient

  data class Packet(val message: String)

  private val jsonMediaType = MediaTypes.APPLICATION_JSON.asMediaType()
  private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()
  private val weirdMediaType = "application/weird".asMediaType()

  @Inject private lateinit var moshi: Moshi
  @Inject private lateinit var jettyService: JettyService
  @Inject lateinit var entriesProvider: Provider<List<WebProxyEntry>>

  private val packetJsonAdapter get() = moshi.adapter(Packet::class.java)

  @BeforeEach
  internal fun setUp() {
    upstreamServer.start()
  }

  @AfterEach
  internal fun tearDown() {
    upstreamServer.shutdown()
  }

  @Test
  internal fun postJsonExpectJsonPathNotFound() {
    val requestContent = packetJsonAdapter.toJson(Packet("my friend"))
    val request = post("/unknown", jsonMediaType, requestContent, jsonMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  internal fun postTextExpectTextPathNotFound() {
    val request = post("/unknown", plainTextMediaType, "my friend", plainTextMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  internal fun postArbitraryExpectArbitraryPathNotFound() {
    val request = post("/unknown", weirdMediaType, "my friend", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  internal fun getJsonPathNotFound() {
    val request = get("/unknown", jsonMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  internal fun getTextPathNotFound() {
    val request = get("/unknown", plainTextMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  internal fun getArbitraryPathNotFound() {
    val request = get("/unknown", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  internal fun headPathNotFound() {
    val request = head("/unknown", weirdMediaType)
    val response = httpClient.newCall(request).execute()
    assertThat(response.code()).isEqualTo(404)
  }

  @Test
  internal fun entryBinding() {
    assertThat(entriesProvider.get().size).isEqualTo(1)
  }

  private fun head(path: String, acceptedMediaType: MediaType? = null): okhttp3.Request {
    return okhttp3.Request.Builder()
        .head()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  private fun get(path: String, acceptedMediaType: MediaType? = null): okhttp3.Request {
    return okhttp3.Request.Builder()
        .get()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  private fun post(
    path: String,
    contentType: MediaType,
    content: String,
    acceptedMediaType: MediaType? = null
  ): okhttp3.Request {
    return okhttp3.Request.Builder()
        .post(RequestBody.create(contentType, content))
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  class TestModule(private val upstreamServer: MockWebServer) : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebProxyActionModule())
      multibind<WebActionEntry>().toInstance(WebActionEntry<WebProxyAction>())
      multibind<WebProxyEntry>().toProvider(object: Provider<WebProxyEntry> {
        override fun get(): WebProxyEntry {
          return WebProxyEntry("/local/prefix", upstreamServer.url("/").toString())
        }
      })
    }
  }



//  @Inject lateinit var entriesProvider: Provider<List<WebProxyEntry>>
//
//  private val upstreamServer = MockWebServer()
//
//  @MiskTestModule
//  val module = TestModule(upstreamServer)
//
//  @BeforeEach
//  internal fun setUp() {
//    upstreamServer.start()
//    val hello = WebProxyActionTest::class.functions.iterator().next()
//    interceptor = interceptorFactoryProvider.get().create(hello.asAction()) as WebProxyAction
//  }
//
//  fun theFunction(): String {
//    return "hello"
//  }
//
//  @AfterEach
//  internal fun tearDown() {
//    upstreamServer.shutdown()
//  }
//
//  class TestModule(val upstreamServer: MockWebServer) : KAbstractModule() {
//    override fun configure() {
//      install(MiskServiceModule())
//      install(WebProxyActionModule())
//      install(ConfigModule.create<HttpClientsConfig>("http_clients", HttpClientsConfig(
//          endpoints = mapOf(
//              "web_proxy_interceptor" to HttpClientEndpointConfig("http://localhost")
//          ))))
//
//      multibind<WebProxyEntry>().toProvider(object: Provider<WebProxyEntry> {
//        override fun get(): WebProxyEntry {
//          return WebProxyEntry(
//              "/local/prefix/",
//              upstreamServer.url("/")
//          )
//        }
//      })
//    }
//  }
//
  @Test
  internal fun testBindings() {
    val mappingBindings = entriesProvider.get()
    println(mappingBindings.first().toString())
    assertThat(mappingBindings.size == 1)
  }

  @Test
  internal fun mappingLocalWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("local/prefix",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun mappingLocalWithTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/local/prefix/",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun mappingToApiPrefix() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/api/test/prefix/",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun mappingUpstreamUrlToFile() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/local/prefix/",
          upstreamServer.url("/test.js"))
    }
  }

  @Test
  internal fun mappingUpstreamUrlWithPathSegments() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/local/prefix/",
          upstreamServer.url("/upstream/prefix/"))
    }
  }

//  @Test
//  internal fun requestForwardedToUpstreamServerIfPathMatchesWithTrailingSlash() {
//    upstreamServer.enqueue(MockResponse()
//        .setResponseCode(418)
//        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
//        .setBody("I am an intercepted response!"))
//
//    val request = Request(
//        HttpUrl.parse("http://localpost/local/prefix/tacos/")!!,
//        body = Buffer()
//    )
//
//    val response = interceptor.intercept(
//        ResourceInterceptorCommon.FakeNetworkChain(request))
//
//    assertThat(response.statusCode).isEqualTo(418)
//    assertThat(response.headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
//    assertThat(response.readUtf8()).isEqualTo("I am an intercepted response!")
//
//    val recordedRequest = upstreamServer.takeRequest()
//    assertThat(recordedRequest.path).isEqualTo("/local/prefix/tacos/")
//  }
//
//  @Test
//  internal fun requestForwardedToUpstreamServerIfPathMatchesWithoutTrailingSlash() {
//    upstreamServer.enqueue(MockResponse()
//        .setResponseCode(418)
//        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
//        .setBody("I am an intercepted response!"))
//
//    val request = Request(
//        HttpUrl.parse("http://localpost/local/prefix/tacos")!!,
//        body = Buffer()
//    )
//
//    val response = interceptor.intercept(
//        ResourceInterceptorCommon.FakeNetworkChain(request))
//
//    assertThat(response.statusCode).isEqualTo(418)
//    assertThat(response.headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
//    assertThat(response.readUtf8()).isEqualTo("I am an intercepted response!")
//
//    val recordedRequest = upstreamServer.takeRequest()
//    assertThat(recordedRequest.path).isEqualTo("/local/prefix/tacos")
//  }
//
//  @Test
//  internal fun requestNotForwardedToUpstreamServerIfPathDoesNotMatch() {
//    val fakeRequest = Request(
//        HttpUrl.parse("http://local/notredirectedprefix/tacos")!!,
//        body = Buffer()
//    )
//
//    val response = interceptor.intercept(
//        ResourceInterceptorCommon.FakeNetworkChain(fakeRequest))
//    assertThat(response.readUtf8()).isEqualTo("I am not intercepted")
//
//    assertThat(upstreamServer.requestCount).isZero()
//  }
//
//  @Test
//  internal fun returnsServerNotReachable() {
//    val urlThatFailed = upstreamServer.url("/local/prefix/tacos")
//    upstreamServer.shutdown()
//
//    val request = Request(
//        HttpUrl.parse("http://localpost/local/prefix/tacos")!!,
//        body = Buffer()
//    )
//
//    val response = interceptor.intercept(
//        ResourceInterceptorCommon.FakeNetworkChain(request))
//
//    assertThat(response.readUtf8()).isEqualTo("Failed to fetch upstream URL $urlThatFailed")
//    assertThat(response.statusCode).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE)
//    assertThat(response.headers["Content-Type"]).isEqualTo("text/plain; charset=utf-8")
//  }

}