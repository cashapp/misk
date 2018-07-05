package misk.web.actions

import misk.web.NetworkChain
import misk.web.Request
import misk.web.Response
import misk.web.ResponseBody
import misk.web.toResponseBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import java.net.HttpURLConnection
import kotlin.reflect.KFunction

internal class UpstreamResourceInterceptorTest {
  val upstreamServer = MockWebServer()
  lateinit var mapping: UpstreamResourceInterceptor.Mapping
  lateinit var mappingTestMapping: UpstreamResourceInterceptor.Mapping
  lateinit var interceptor: UpstreamResourceInterceptor
  lateinit var mappingTestInterceptor: UpstreamResourceInterceptor

  @BeforeEach
  internal fun setUp() {
    upstreamServer.start()

    mapping = UpstreamResourceInterceptor.Mapping(
        "/local/prefix/",
        upstreamServer.url("/"))

    interceptor = UpstreamResourceInterceptor(OkHttpClient(), mutableListOf(mapping))
  }

  @AfterEach
  internal fun tearDown() {
    upstreamServer.shutdown()
  }

  @Test
  internal fun mappingFailsIfLocalWithoutLeadingSlash() {
    var failed = false

    try {
      mappingTestMapping = UpstreamResourceInterceptor.Mapping(
          "local/prefix/",
          upstreamServer.url("/"))
    } catch (e: IllegalArgumentException) {
      failed = true
    }

    assertThat(failed).isTrue()
  }

  @Test
  internal fun mappingFailsIfLocalWithoutTrailingSlash() {
    var failed = false

    try {
      mappingTestMapping = UpstreamResourceInterceptor.Mapping(
          "/local/prefix",
          upstreamServer.url("/"))
    } catch (e: IllegalArgumentException) {
      failed = true
    }

    assertThat(failed).isTrue()
  }

  @Test
  internal fun mappingFailsIfUpstreamHasPathSegments() {
    var failed = false

    try {
      mappingTestMapping = UpstreamResourceInterceptor.Mapping(
          "/local/prefix/",
          upstreamServer.url("/upstream/prefix/"))
    } catch (e: IllegalArgumentException) {
      failed = true
    }

    assertThat(failed).isTrue()
  }

  @Test
  internal fun requestForwardedToUpstreamServerIfPathMatchesWithTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(404)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val request = Request(
        HttpUrl.parse("http://localpost/local/prefix/tacos/")!!,
        body = Buffer()
    )

    val response = interceptor.intercept(FakeNetworkChain(request))

    assertThat(response.statusCode).isEqualTo(404)
    assertThat(response.headers["UpstreamHeader"]).isEqualTo("UpstreamHeaderValue")
    assertThat(response.readUtf8()).isEqualTo("I am an intercepted response!")

    val recordedRequest = upstreamServer.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/local/prefix/tacos/")
  }

  @Test
  internal fun requestForwardedToUpstreamServerIfPathMatchesWithoutTrailingSlash() {
    upstreamServer.enqueue(MockResponse()
        .setResponseCode(404)
        .addHeader("UpstreamHeader", "UpstreamHeaderValue")
        .setBody("I am an intercepted response!"))

    val request = Request(
        HttpUrl.parse("http://localpost/local/prefix/tacos")!!,
        body = Buffer()
    )

    val response = interceptor.intercept(FakeNetworkChain(request))

    assertThat(response.statusCode).isEqualTo(404)
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

    val response = interceptor.intercept(FakeNetworkChain(fakeRequest))
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

    val response = interceptor.intercept(FakeNetworkChain(request))

    assertThat(response.readUtf8()).isEqualTo("Failed to fetch upstream URL $urlThatFailed")
    assertThat(response.statusCode).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE)
    assertThat(response.headers["Content-Type"]).isEqualTo("text/plain; charset=utf-8")
  }

  private fun Response<*>.readUtf8(): String {
    val buffer = Buffer()
    (body as ResponseBody).writeTo(buffer)
    return buffer.readUtf8()
  }

  class FakeNetworkChain(
    override val request: Request
  ) : NetworkChain {
    override val action: WebAction
      get() = throw AssertionError()

    override val function: KFunction<*>
      get() = throw AssertionError()

    override fun proceed(request: Request): Response<*> {
      return Response("I am not intercepted".toResponseBody())
    }
  }
}