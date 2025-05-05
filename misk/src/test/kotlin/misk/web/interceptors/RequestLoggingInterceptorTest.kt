package misk.web.interceptors

import com.google.common.testing.FakeTicker
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.config.AppNameModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.interceptors.hooks.QuokkaLovingTransformer
import misk.web.interceptors.hooks.RegularSuperlativeEnhancingTransformer
import misk.web.interceptors.hooks.RequestResponseHook
import misk.web.interceptors.hooks.SillySuperlativeEnhancingTransformer
import misk.web.interceptors.hooks.ThrowingTransformer
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import wisp.logging.getLogger
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.findAnnotation
import kotlin.test.assertEquals

@MiskTest(startService = true)
internal class RequestLoggingInterceptorTest {
  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var fakeTicker: FakeTicker
  @Inject private lateinit var logCollector: LogCollector

  @BeforeEach
  fun setUp() {
    fakeTicker.setAutoIncrementStep(100L, TimeUnit.MILLISECONDS)
  }

  @Test
  fun nothing() {
    var messages = logCollector.takeMessages(FakeRequestResponseHook::class)
    assertEquals(0, messages.size)
    assertThat(
      invoke(
        "/call/logNothing/hello",
        "caller"
      ).isSuccessful
    ).isTrue()
    messages = logCollector.takeMessages(FakeRequestResponseHook::class)
    assertEquals(0, messages.size)
  }

  @Test
  fun everything() {
    var messages = logCollector.takeMessages(FakeRequestResponseHook::class)
    assertEquals(0, messages.size)
    assertThat(
      invoke(
        "/call/logEverything/hello",
        "caller"
      ).isSuccessful
    ).isTrue()

    messages = logCollector.takeMessages(FakeRequestResponseHook::class)
    assertEquals(1, messages.size)
    assertEquals(
      "/call/logEverything/hello principal=caller time=100.0 ms code=200 request=[[hello]] response=[echo: hello]",
      messages[0]
    )
  }

  @Test
  fun exceptionThrown() {
    var messages = logCollector.takeMessages(FakeRequestResponseHook::class)
    assertEquals(0, messages.size)
    assertThat(invoke("/call/exceptionThrowingRequestLogging/fail", "caller").code)
      .isEqualTo(500)
    messages = logCollector.takeMessages(FakeRequestResponseHook::class)
    assertEquals(1, messages.size)
    assertEquals(
      "/call/exceptionThrowingRequestLogging/fail principal=caller time=100.0 ms code=200 request=[[fail]] response=[null]",
      messages[0]
    )
  }

  @Test
  fun `requestResponseBodyTransformer applies all relevant transformers`() {
    assertThat(invoke("/call/logEverything/Quokka", "caller").isSuccessful).isTrue()
  }

  @Test
  fun `requestResponseBodyTransformer contains explosions`() {
    assertThat(
      invoke(
        "/call/logEverything/Oppenheimer-the-bestest",
        "caller"
      ).isSuccessful
    ).isTrue()
  }

  fun invoke(path: String, asService: String? = null): Response {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(path)
      .build()

    val request = Request.Builder()
      .url(url)
      .get()
    asService?.let { request.addHeader(FakeCallerAuthenticator.SERVICE_HEADER, it) }
    return httpClient.newCall(request.build()).execute()
  }

  class FakeRequestResponseHook private constructor() : RequestResponseHook {
    @Singleton
    class Factory @Inject constructor() : RequestResponseHook.Factory {
      override fun create(action: Action): RequestResponseHook? {
        action.function.findAnnotation<FakeRequestResponse>() ?: return null
        return FakeRequestResponseHook()
      }
    }

    override fun handle(
      caller: MiskCaller?,
      httpCall: HttpCall,
      requestResponse: RequestResponseBody?,
      elapsed: Duration,
      elapsedToString: String,
      error: Throwable?
    ) {
      logger.info(
        "${httpCall.url.encodedPath} principal=${caller?.principal} time=${elapsedToString} " +
          "code=${httpCall.statusCode} request=[${requestResponse?.request}] " +
          "response=[${requestResponse?.response}]"
      )
    }

    companion object {
      private val logger = getLogger<FakeRequestResponseHook>()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AppNameModule("test"))
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(TestActionsModule())

      install(LogCollectorModule())

      multibind<RequestResponseHook.Factory>().to<FakeRequestResponseHook.Factory>()

      // Note: the order of these registrations is intentional as it impacts behavior in same tests
      multibind<RequestLoggingTransformer>().to<ThrowingTransformer>()
      multibind<RequestLoggingTransformer>().to<RegularSuperlativeEnhancingTransformer>()
      multibind<RequestLoggingTransformer>().to<QuokkaLovingTransformer>()
      multibind<RequestLoggingTransformer>().to<SillySuperlativeEnhancingTransformer>()
    }
  }

  class TestActionsModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<AuditNothingAction>())
      install(WebActionModule.create<AuditEverythingAction>())
      install(WebActionModule.create<AuditExceptionThrowingRequestAction>())
      install(WebActionModule.create<AuditNoRequestAction>())
    }
  }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class FakeRequestResponse

internal class AuditNothingAction @Inject constructor() : WebAction {
  @Get("/call/logNothing/{message}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call(@PathParam message: String) = "echo: $message"
}

internal class AuditEverythingAction @Inject constructor() : WebAction {
  @Get("/call/logEverything/{message}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @FakeRequestResponse
  fun call(@PathParam message: String) = "echo: $message"
}

internal class AuditExceptionThrowingRequestAction @Inject constructor() : WebAction {
  @Get("/call/exceptionThrowingRequestLogging/{message}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @FakeRequestResponse
  fun call(@PathParam message: String): String = throw IllegalStateException(message)
}

internal class AuditNoRequestAction @Inject constructor() : WebAction {
  @Get("/call/noRequestLogging/{message}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call(@PathParam message: String) = "echo: $message"
}
