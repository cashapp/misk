package misk.web.proxy

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebActionEntry
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.mediatype.asMediaType
import misk.web.readUtf8
import misk.web.toMisk
import okhttp3.MediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlin.test.assertFailsWith

@MiskTest
class WebProxyActionTest {
  private val upstreamServer = MockWebServer()

  @MiskTestModule
  val module = TestModule(upstreamServer)

  @Inject private lateinit var optionalBinder: OptionalBinder

  private val plainTextMediaType = MediaTypes.TEXT_PLAIN_UTF8.asMediaType()
  private val weirdMediaType = "application/weird".asMediaType()

  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var actionEntriesProvider: Provider<List<WebActionEntry>>
  @Inject private lateinit var proxyEntriesProvider: Provider<List<WebProxyEntry>>

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
      WebProxyEntry("local/prefix", upstreamServer.url("/"))
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

    val responseAsync = GlobalScope.async {
      optionalBinder.proxyClient.newCall(
          get("/local/prefix/tacos/", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest!!.getHeader("Forwarded")).isEqualTo(
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
        GlobalScope.async {
          optionalBinder.proxyClient.newCall(get("/local/prefix/tacos", weirdMediaType)).execute()
              .toMisk()
        }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(upstreamReceivedRequest!!.getHeader("Forwarded")).isEqualTo(
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
    val response = optionalBinder.proxyClient.newCall(request).execute().toMisk()
    assertThat(response.readUtf8()).isEqualTo("""
      |Nothing found at /local/notredirectedprefix/tacos.
      |
      |Received:
      |GET /local/notredirectedprefix/tacos
      |Accept: application/weird
      |""".trimMargin())

    assertThat(upstreamServer.requestCount).isZero()
  }

  @Test
  internal fun postForwardedPathMatchTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val response = GlobalScope.async {
      optionalBinder.proxyClient.newCall(
          post("/local/prefix/tacos/", weirdMediaType, "my taco", weirdMediaType)).execute()
          .toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest!!.getHeader("Forwarded")).isEqualTo(
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

    val response = GlobalScope.async {
      optionalBinder.proxyClient.newCall(
          post("/local/prefix/tacos", weirdMediaType, "my taco", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest!!.getHeader("Forwarded")).isEqualTo(
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
    val response = optionalBinder.proxyClient.newCall(request).execute().toMisk()
    assertThat(response.readUtf8()).isEqualTo("""
      |Nothing found at /local/notredirectedprefix/tacos.
      |
      |Received:
      |POST /local/notredirectedprefix/tacos
      |Accept: application/weird
      |Content-Type: application/weird; charset=utf-8
      |""".trimMargin())

    assertThat(upstreamServer.requestCount).isZero()
  }

  @Test
  internal fun returnsNotFoundWhenServerNotReachable() {
    upstreamServer.shutdown()

    val request = get("/local/prefix/tacos", plainTextMediaType)
    val response = optionalBinder.proxyClient.newCall(request).execute().toMisk()

    assertThat(response.readUtf8()).isEqualTo(
        "Nothing found at /local/prefix/tacos")
    assertThat(response.statusCode).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
    assertThat(response.headers["Content-Type"]).isEqualTo(plainTextMediaType.toString())
  }

  @Test
  internal fun getForwardedLongPathMatchTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(418)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val responseAsync = GlobalScope.async {
      optionalBinder.proxyClient.newCall(
          get("/local/prefix/tacos/another/long/prefix/see/if/forwards/", weirdMediaType)).execute()
          .toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest!!.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo(
        "/local/prefix/tacos/another/long/prefix/see/if/forwards/")

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

    val responseAsync = GlobalScope.async {
      optionalBinder.proxyClient.newCall(
          get("/local/prefix/tacos////see/if/forwards/", weirdMediaType)).execute().toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest!!.getHeader("Forwarded")).isEqualTo(
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

    val responseAsync = GlobalScope.async {
      optionalBinder.proxyClient.newCall(
          get("/local/prefix/tacos/.test/.config/.ssh/see/if/forwards/", weirdMediaType)).execute()
          .toMisk()
    }

    val upstreamReceivedRequest = upstreamServer.takeRequest(200, TimeUnit.MILLISECONDS)
    assertThat(
        upstreamReceivedRequest!!.getHeader("Forwarded")).isEqualTo(
        "for=; by=${jettyService.httpServerUrl.newBuilder().encodedPath("/")}")
    assertThat(upstreamServer.requestCount).isNotZero()
    assertThat(upstreamReceivedRequest.path).isEqualTo(
        "/local/prefix/tacos/.test/.config/.ssh/see/if/forwards/")

    runBlocking {
      assertThat(responseAsync.await().statusCode).isEqualTo(418)
      assertThat(responseAsync.await().headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
      assertThat(responseAsync.await().readUtf8()).isEqualTo("I am an intercepted response!")
    }
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
        .post(content.toRequestBody(contentType))
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(path).build())
        .header("Accept", acceptedMediaType.toString())
        .build()
  }

  class TestModule(private val upstreamServer: MockWebServer) : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.createWithPrefix<WebProxyAction>("/local/prefix/"))
      multibind<WebProxyEntry>().toProvider(
          Provider<WebProxyEntry> {
            WebProxyEntry("/local/prefix/", upstreamServer.url("/").toString())
          })
      install(WebTestingModule())
    }
  }
}
