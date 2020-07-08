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
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class RequestLoggingInterceptorTest {
  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var fakeTicker: FakeTicker
  @Inject private lateinit var fakeRandom: FakeRandom

  @BeforeEach
  fun setUp() {
    fakeTicker.setAutoIncrementStep(100L, TimeUnit.MILLISECONDS)
  }

  @Test
  fun includesBody() {
    assertThat(invoke("/call/includeBodyRequestLogging/hello", "caller").isSuccessful).isTrue()
    val messages = logCollector.takeMessages(RequestLoggingInterceptor::class)
    assertThat(messages).containsExactly(
      "IncludeBodyRequestLoggingAction principal=caller time=100.0 ms code=200 request=[hello] response=echo: hello"
    )
  }

  @Test
  fun excludesBody() {
    assertThat(invoke("/call/excludeBodyRequestLogging/hello", "caller").isSuccessful).isTrue()
    val messages = logCollector.takeMessages(RequestLoggingInterceptor::class)
    assertThat(messages).containsExactly(
      "ExcludeBodyRequestLoggingAction principal=caller time=100.0 ms code=200"
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
  fun notSampled() {
    fakeRandom.nextDouble = 0.7
    assertThat(invoke("/call/sampledRequestLogging/hello", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).isEmpty()
  }

  @Test
  fun sampled() {
    fakeRandom.nextDouble = 0.2
    assertThat(invoke("/call/sampledRequestLogging/hello", "caller").isSuccessful).isTrue()
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).isNotEmpty()
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

  internal class IncludeBodyRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/includeBodyRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(sampling = 1.0, includeBody = true)
    fun call(@PathParam message: String) = "echo: $message"
  }

  internal class ExcludeBodyRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/excludeBodyRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(sampling = 1.0, includeBody = false)
    fun call(@PathParam message: String) = "echo: $message"
  }

  internal class ExceptionThrowingRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/exceptionThrowingRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(sampling = 1.0, includeBody = true)
    fun call(@PathParam message: String): String = throw IllegalStateException(message)
  }

  internal class SampledRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/sampledRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    @LogRequestResponse(sampling = 0.4, includeBody = true)
    fun call(@PathParam message: String) = "echo: $message"
  }

  internal class NoRequestLoggingAction @Inject constructor() : WebAction {
    @Get("/call/noRequestLogging/{message}")
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@PathParam message: String) = "echo: $message"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AccessControlModule())
      install(WebTestingModule())
      install(LogCollectorModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(
        WebActionModule.create<IncludeBodyRequestLoggingAction>())
      install(
        WebActionModule.create<ExcludeBodyRequestLoggingAction>())
      install(
        WebActionModule.create<ExceptionThrowingRequestLoggingAction>())
      install(WebActionModule.create<NoRequestLoggingAction>())
      install(WebActionModule.create<SampledRequestLoggingAction>())
    }
  }
}