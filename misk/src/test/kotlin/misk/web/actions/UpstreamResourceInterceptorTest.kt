package misk.web.actions

import misk.inject.KAbstractModule
import misk.testing.MiskTestModule
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Request
import misk.web.Response
import misk.web.ResponseBody
import misk.web.WebModule
import misk.web.WebTestingModule
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
import javax.inject.Inject
import kotlin.reflect.KFunction
import kotlin.test.assertFailsWith

internal class UpstreamResourceInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  private val upstreamServer = MockWebServer()
  private lateinit var interceptor: UpstreamResourceInterceptor

  @Inject private lateinit var mappingBindings: List<UpstreamResourceInterceptor.Mapping>

  @BeforeEach
  internal fun setUp() {
    upstreamServer.start()

    interceptor = UpstreamResourceInterceptor(OkHttpClient(), mutableListOf(
        UpstreamResourceInterceptor.Mapping("/local/prefix/",
            upstreamServer.url("/"), "/", UpstreamResourceInterceptor.Mode.SERVER)))
  }

  @AfterEach
  internal fun tearDown() {
    upstreamServer.shutdown()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      multibind<UpstreamResourceInterceptor.Mapping>().toInstance(UpstreamResourceInterceptor.Mapping(
          "/local/prefix/",
          HttpUrl.parse("http://localhost:418")!!, "/", UpstreamResourceInterceptor.Mode.SERVER
      ))
    }
  }

  @Test
  internal fun testBindings() {

    assert(mappingBindings.size == 1)
  }

  @Test
  internal fun mappingLocalWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      UpstreamResourceInterceptor.Mapping("local/prefix/",
          upstreamServer.url("/"), "/", UpstreamResourceInterceptor.Mode.SERVER)
    }
  }

  @Test
  internal fun mappingLocalWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      UpstreamResourceInterceptor.Mapping("/local/prefix",
          upstreamServer.url("/"), "/", UpstreamResourceInterceptor.Mode.SERVER)
    }
  }

  @Test
  internal fun mappingToApiPrefix() {
    assertFailsWith<IllegalArgumentException> {
      UpstreamResourceInterceptor.Mapping("/api/test/prefix/",
          upstreamServer.url("/"), "/", UpstreamResourceInterceptor.Mode.SERVER)
    }
  }

  @Test
  internal fun mappingUpstreamUrlToFile() {
    assertFailsWith<IllegalArgumentException> {
      UpstreamResourceInterceptor.Mapping("/local/prefix/",
          upstreamServer.url("/test.js"), "/", UpstreamResourceInterceptor.Mode.SERVER)
    }
  }

  @Test
  internal fun mappingUpstreamUrlWithPathSegments() {
    assertFailsWith<IllegalArgumentException> {
      UpstreamResourceInterceptor.Mapping("/local/prefix/",
          upstreamServer.url("/upstream/prefix/"), "/", UpstreamResourceInterceptor.Mode.SERVER)
    }
  }

  @Test
  internal fun mappingUpstreamJarWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      UpstreamResourceInterceptor.Mapping("local/prefix/",
          upstreamServer.url("/"), "place/in/jar/", UpstreamResourceInterceptor.Mode.SERVER)
    }
  }

  @Test
  internal fun mappingUpstreamJarWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      UpstreamResourceInterceptor.Mapping("/local/prefix",
          upstreamServer.url("/"), "/place/in/jar", UpstreamResourceInterceptor.Mode.SERVER)
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

    val response = interceptor.intercept(FakeNetworkChain(request))

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

    val response = interceptor.intercept(FakeNetworkChain(request))

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