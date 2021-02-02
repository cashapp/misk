package misk.web.interceptors

import com.google.common.testing.FakeTicker
import misk.inject.KAbstractModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.random.FakeRandom
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestClient
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class RequestLoggingInterceptorTest {
  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var webTestClient: WebTestClient
  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var fakeTicker: FakeTicker
  @Inject private lateinit var fakeRandom: FakeRandom

  @BeforeEach
  fun setUp() {
    fakeTicker.setAutoIncrementStep(100L, TimeUnit.MILLISECONDS)
  }

  @Test
  fun rateLimiting_includesBody() {
    fakeRandom.nextDouble = 0.1
    assertThat(invoke("/call/rateLimitingIncludesBodyRequestLogging/hello", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "RateLimitingIncludesBodyRequestLoggingAction principal=caller time=100.0 ms code=200 request=[hello] response=echo: hello"
    )

    // Setting to low value to show that even though it is less than the bodySampling value in the
    // LogRequestResponse, because the LogRateLimiter does not acquire a bucket, the request and
    // response bodies are also not logged
    fakeRandom.nextDouble = 0.01
    assertThat(invoke("/call/rateLimitingIncludesBodyRequestLogging/hello2", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).isEmpty()

    fakeTicker.advance(1, TimeUnit.SECONDS)

    fakeRandom.nextDouble = 0.2
    assertThat(invoke("/call/rateLimitingIncludesBodyRequestLogging/hello3", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "RateLimitingIncludesBodyRequestLoggingAction principal=caller time=100.0 ms code=200 request=[hello3] response=echo: hello3"
    )

    fakeTicker.advance(1, TimeUnit.SECONDS)

    // The random value exceeds the bodySampling value on the annotation, so request and response
    // bodies are not logged even though the LogRateLimiter acquires a bucket
    fakeRandom.nextDouble = 0.6
    assertThat(invoke("/call/rateLimitingIncludesBodyRequestLogging/hello4", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "RateLimitingIncludesBodyRequestLoggingAction principal=caller time=100.0 ms code=200"
    )
  }

  @Test
  fun rateLimiting_excludesBody() {
    assertThat(invoke("/call/rateLimitingRequestLogging/hello", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "RateLimitingRequestLoggingAction principal=caller time=100.0 ms code=200"
    )

    assertThat(invoke("/call/rateLimitingRequestLogging/hello2", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).isEmpty()

    fakeTicker.advance(1, TimeUnit.SECONDS)

    assertThat(invoke("/call/rateLimitingRequestLogging/hello3", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "RateLimitingRequestLoggingAction principal=caller time=100.0 ms code=200"
    )
  }

  @Test
  fun exceptionThrown() {
    assertThat(invoke("/call/exceptionThrowingRequestLogging/fail", "caller").code).isEqualTo(500)
    val messages = logCollector.takeMessages(RequestLoggingInterceptor::class)
    assertThat(messages).containsExactly(
      "ExceptionThrowingRequestLoggingAction principal=caller time=100.0 ms failed request=[fail]"
    )
  }

  @Test
  fun noRateLimiting() {
    fakeRandom.nextDouble = 0.7
    for (i in 0..10) {
      assertThat(invoke("/call/noRateLimitingRequestLogging/hello", "caller").isSuccessful).isTrue()
      val messages = logCollector.takeMessages(RequestLoggingInterceptor::class)
      assertThat(messages).containsExactly(
        "NoRateLimitingRequestLoggingAction principal=caller time=100.0 ms code=200"
      )
    }
  }

  @Test
  fun noRequestLoggingIfMissingAnnotation() {
    assertThat(invoke("/call/noRequestLogging/hello", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).isEmpty()
  }

  fun invoke(path: String, asService: String? = null): okhttp3.Response {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(path)
      .build()

    val request = okhttp3.Request.Builder()
      .url(url)
      .get()
    asService?.let { request.addHeader(FakeCallerAuthenticator.SERVICE_HEADER, it) }
    return httpClient.newCall(request.build()).execute()
  }

  @Test
  fun capturesSubsetOfHeaders() {
    val headerToNotLog = "X-Header-To-Not-Log"
    val headerValueToNotLog = "some-value"
    assertThat(webTestClient.call("/call/withHeaders") {
      post("hello".toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE))
      addHeader(headerToNotLog, headerValueToNotLog)
    }.response.isSuccessful)
      .isTrue()
    val messages = logCollector.takeMessages(RequestLoggingInterceptor::class)
    assertThat(messages).containsExactly(
      "RequestLoggingActionWithHeaders principal=unknown time=100.0 ms code=200 request=[hello, HeadersCapture(headers={accept=[*/*], accept-encoding=[gzip], connection=[keep-alive], content-length=[5], content-type=[application/json;charset=UTF-8]})] response=echo: hello"
    )
    assertThat(messages[0]).doesNotContain(headerToNotLog)
    assertThat(messages[0]).doesNotContain(headerToNotLog.toLowerCase())
    assertThat(messages[0]).doesNotContain(headerValueToNotLog)
  }

  internal class RateLimitingRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/rateLimitingRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(ratePerSecond = 1L, errorRatePerSecond = 2L)
    fun call(@PathParam message: String) = "echo: $message"
  }

  internal class RateLimitingIncludesBodyRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/rateLimitingIncludesBodyRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(ratePerSecond = 1L, errorRatePerSecond = 2L, bodySampling = 0.5, errorBodySampling = 1.0)
    fun call(@PathParam message: String) = "echo: $message"
  }

  internal class NoRateLimitingRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/noRateLimitingRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(ratePerSecond = 0L, errorRatePerSecond = 0L, bodySampling = 0.5, errorBodySampling = 0.5)
    fun call(@PathParam message: String) = "echo: $message"
  }

  internal class ExceptionThrowingRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/exceptionThrowingRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(ratePerSecond = 1L, errorRatePerSecond = 2L, bodySampling = 0.1, errorBodySampling = 1.0)
    fun call(@PathParam message: String): String = throw IllegalStateException(message)
  }

  internal class NoRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/noRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@PathParam message: String) = "echo: $message"
  }

  internal class RequestLoggingActionWithHeaders @Inject constructor() : WebAction {
    @Post("/call/withHeaders")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(ratePerSecond = 1L, errorRatePerSecond = 2L, bodySampling = 1.0, errorBodySampling = 1.0)
    fun call(@RequestBody message: String, @RequestHeaders headers: Headers) = "echo: $message"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule())
      install(WebTestingModule())
      install(LogCollectorModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(TestActionsModule())
    }
  }

  class TestActionsModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<RateLimitingRequestLoggingAction>())
      install(WebActionModule.create<RateLimitingIncludesBodyRequestLoggingAction>())
      install(WebActionModule.create<NoRateLimitingRequestLoggingAction>())
      install(WebActionModule.create<ExceptionThrowingRequestLoggingAction>())
      install(WebActionModule.create<NoRequestLoggingAction>())
      install(WebActionModule.create<RequestLoggingActionWithHeaders>())
    }
  }
}
