package misk.web.proxy

import com.google.inject.name.Names
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import misk.client.HttpClientModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Response
import misk.web.WebTestingModule
import misk.web.actions.WebActionEntry
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import misk.web.readUtf8
import misk.web.toMisk
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlin.test.assertFailsWith

@MiskTest(startService = true)
class WebProxyActionTest {
  private val upstreamServer = MockWebServer()

  @MiskTestModule
  val module = TestModule(upstreamServer)

  @Inject @Named("web_proxy_action_test") private lateinit var httpClient: OkHttpClient

  private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()
  private val weirdMediaType = "application/weird".asMediaType()

  @Inject private lateinit var jettyService: JettyService
  @Inject lateinit var actionEntriesProvider: Provider<List<WebActionEntry>>
  @Inject lateinit var proxyEntriesProvider: Provider<List<WebProxyEntry>>

  @Volatile private var threadResponse: Response<*> = Response(
      "Uninitialized".toResponseBody(),
      Headers.of("Content-Type", plainTextMediaType.toString()),
      HttpURLConnection.HTTP_INTERNAL_ERROR
  )

  @BeforeEach
  internal fun setUp() {
    upstreamServer.start()
  }

  @AfterEach
  internal fun tearDown() {
    upstreamServer.shutdown()
  }

  @Test
  internal fun entriesInjected() {
    val actionEntries = actionEntriesProvider.get()
    actionEntries.forEach { println(it) }
    assertThat(actionEntries.size == 1)

    val proxyEntries = proxyEntriesProvider.get()
    println(proxyEntries.first().toString())
    assertThat(proxyEntries.size == 1)
  }

  @Test
  internal fun entryLocalWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("local/prefix",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun entryLocalWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/local/prefix",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun entryToApiPrefix() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/api/test/prefix/",
          upstreamServer.url("/"))
    }
  }

  @Test
  internal fun entryUpstreamUrlToFile() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/local/prefix/",
          upstreamServer.url("/test.js"))
    }
  }

  @Test
  internal fun entryUpstreamUrlWithPathSegments() {
    assertFailsWith<IllegalArgumentException> {
      WebProxyEntry("/local/prefix/",
          upstreamServer.url("/upstream/prefix/"))
    }
  }

  @Test
  internal fun getForwardedPathMatchTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val responseAsync = async {
      httpClient.newCall(
          get("/local/prefix/tacos/", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo("/local/prefix/tacos/")

    runBlocking {
      assertThat(responseAsync.await().statusCode).isEqualTo(418)
      assertThat(responseAsync.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(responseAsync.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
  }

  @Test
  internal fun getForwardedPathMatchNoTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val responseAsync =
        async { httpClient.newCall(get("/local/prefix/tacos", weirdMediaType)).execute().toMisk() }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo("/local/prefix/tacos")

    runBlocking {
      assertThat(responseAsync.await().statusCode).isEqualTo(418)
      assertThat(responseAsync.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(responseAsync.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
  }

  @Test
  internal fun getNotForwardedPathNoMatch() {
    val request = get("/local/notredirectedprefix/tacos", weirdMediaType)
    val response = httpClient.newCall(request).execute().toMisk()
    assertThat(response.readUtf8()).isEqualTo("Nothing found at /local/notredirectedprefix/tacos")

    assertThat(upstreamServer.requestCount).isZero()
  }

  @Test
  internal fun postForwardedPathMatchTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val response = async {
      httpClient.newCall(
          post("/local/prefix/tacos/", weirdMediaType, "my taco", weirdMediaType)).execute()
          .toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo("/local/prefix/tacos/")

    runBlocking {
      assertThat(response.await().statusCode).isEqualTo(418)
      assertThat(response.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(response.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
  }

  @Test
  internal fun postForwardedPathMatchNoTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val response = async {
      httpClient.newCall(
          post("/local/prefix/tacos", weirdMediaType, "my taco", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo("/local/prefix/tacos")

    runBlocking {
      assertThat(response.await().statusCode).isEqualTo(418)
      assertThat(response.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(response.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
  }

  @Test
  internal fun postNotForwardedPathNoMatch() {
    val request =
        post("/local/notredirectedprefix/tacos", weirdMediaType, "my taco", weirdMediaType)
    val response = httpClient.newCall(request).execute().toMisk()
    assertThat(response.readUtf8()).isEqualTo("Nothing found at /local/notredirectedprefix/tacos")

    assertThat(upstreamServer.requestCount).isZero()
  }

  @Test
  internal fun returnsServerNotReachable() {
    val urlThatFailed = upstreamServer.url("/local/prefix/tacos")

    upstreamServer.shutdown()

    val request = get("/local/prefix/tacos", plainTextMediaType)
    val response = httpClient.newCall(request).execute().toMisk()

    assertThat(response.readUtf8()).isEqualTo(
        "Failed to fetch upstream URL $urlThatFailed")
    assertThat(response.statusCode).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE)
    assertThat(response.headers["Content-Type"]).isEqualTo(plainTextMediaType.toString())
  }

  private fun get(path: String, acceptedMediaType: MediaType? = null): okhttp3.Request {
    return okhttp3.Request.Builder()
        .get()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  @Test
  internal fun getForwardedLongPathMatchTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val responseAsync = async {
      httpClient.newCall(
          get("/local/prefix/tacos/another/long/prefix/see/if/forwards/", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo("/local/prefix/tacos/another/long/prefix/see/if/forwards/")

    runBlocking {
      assertThat(responseAsync.await().statusCode).isEqualTo(418)
      assertThat(responseAsync.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(responseAsync.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
  }

  @Test
  internal fun getForwardedSlashesOnSlashes() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val responseAsync = async {
      httpClient.newCall(
          get("/local/prefix/tacos////see/if/forwards/", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo("/local/prefix/tacos////see/if/forwards/")

    runBlocking {
      assertThat(responseAsync.await().statusCode).isEqualTo(418)
      assertThat(responseAsync.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(responseAsync.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
  }

  @Test
  internal fun getForwardedDotDirectory() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val responseAsync = async {
      httpClient.newCall(
          get("/local/prefix/tacos/.test/.config/.ssh/see/if/forwards/", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo("/local/prefix/tacos/.test/.config/.ssh/see/if/forwards/")

    runBlocking {
      assertThat(responseAsync.await().statusCode).isEqualTo(418)
      assertThat(responseAsync.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(responseAsync.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
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
      install(HttpClientModule("web_proxy_action_test", Names.named("web_proxy_action_test")))
      multibind<WebActionEntry>().toInstance(
          WebActionEntry<WebProxyAction>("/local/prefix/"))
      multibind<WebProxyEntry>().toProvider(
          Provider<WebProxyEntry> {
            WebProxyEntry("/local/prefix/", upstreamServer.url("/").toString())
          })
      install(WebTestingModule())
    }
  }
}