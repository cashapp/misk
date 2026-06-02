package misk.web.interceptors.hooks

import com.google.common.testing.FakeTicker
import jakarta.inject.Inject
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import misk.MiskTestingServiceModule
import misk.audit.AuditRequestResponse
import misk.audit.FakeAuditClient
import misk.audit.FakeAuditClientModule
import misk.config.AppNameModule
import misk.exceptions.BadRequestException
import misk.inject.KAbstractModule
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
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.interceptors.RequestLoggingTransformer
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class AuditClientHookTest {
  @MiskTestModule val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var auditClient: FakeAuditClient
  @Inject private lateinit var webTestClient: WebTestClient
  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var fakeTicker: FakeTicker
  @Inject private lateinit var fakeRandom: FakeRandom

  @BeforeEach
  fun setUp() {
    fakeTicker.setAutoIncrementStep(100L, TimeUnit.MILLISECONDS)
  }

  @Test
  fun auditNothing() {
    assertEquals(0, auditClient.sentEvents.size)
    fakeRandom.nextDouble = 0.1
    assertThat(invoke("/call/logNothing/hello", "test-user").isSuccessful).isTrue()
    assertEquals(0, auditClient.sentEvents.size)
  }

  @Test
  fun auditEverything() {
    assertEquals(0, auditClient.sentEvents.size)
    fakeRandom.nextDouble = 0.1
    assertThat(invoke("/call/logEverything/hello", "test-user").isSuccessful).isTrue()

    assertEquals(
      FakeAuditClient.FakeAuditEvent(
        eventSource = "test",
        eventTarget = "AuditEverythingAction",
        timestampSent = 2147483647,
        applicationName = "test",
        approverLDAP = null,
        automatedChange = false,
        description = "AuditEverythingAction principal=test-user",
        richDescription =
          "AuditEverythingAction principal=test-user time=100.0 ms code=200 request=hello response=echo: hello",
        environment = "testing",
        detailURL = null,
        region = "us-west-2",
        requestorLDAP = "test-user",
      ),
      auditClient.sentEvents.take(),
    )
  }

  @Test
  fun exceptionThrown() {
    assertEquals(0, auditClient.sentEvents.size)
    assertThat(invoke("/call/exceptionThrowingRequestLogging/fail", "test-user").code).isEqualTo(500)
    assertEquals(1, auditClient.sentEvents.size)
    assertEquals(
      FakeAuditClient.FakeAuditEvent(
        eventSource = "test",
        eventTarget = "AuditExceptionThrowingRequestAction",
        timestampSent = 2147483647,
        applicationName = "test",
        approverLDAP = null,
        automatedChange = false,
        description = "AuditExceptionThrowingRequestAction principal=test-user",
        richDescription = "AuditExceptionThrowingRequestAction principal=test-user time=100.0 ms failed",
        environment = "testing",
        detailURL = null,
        region = "us-west-2",
        requestorLDAP = "test-user",
      ),
      auditClient.sentEvents.take(),
    )
  }

  @Test
  fun auditWithHeaders() {
    assertEquals(0, auditClient.sentEvents.size)
    val headerToNotLog = "X-Header-To-Not-Log"
    val headerValueToNotLog = "some-value"
    assertThat(
        webTestClient
          .call("/call/withHeaders") {
            post("hello".toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE))
            addHeader(headerToNotLog, headerValueToNotLog)
          }
          .response
          .isSuccessful
      )
      .isTrue()

    assertEquals(
      FakeAuditClient.FakeAuditEvent(
        eventSource = "test",
        eventTarget = "AuditRequestActionWithHeaders",
        timestampSent = 2147483647,
        applicationName = "test",
        approverLDAP = null,
        automatedChange = false,
        description = "AuditRequestActionWithHeaders principal=unknown",
        richDescription =
          "AuditRequestActionWithHeaders principal=unknown time=100.0 ms code=200 request=hello requestHeaders={accept=[*/*], accept-encoding=[gzip], connection=[keep-alive], content-length=[5], content-type=[application/json;charset=UTF-8]} response=echo: hello responseHeaders={}",
        environment = "testing",
        detailURL = null,
        region = "us-west-2",
        requestorLDAP = FakeAuditClient.DEFAULT_USER,
      ),
      auditClient.sentEvents.take(),
    )
  }

  @Test
  fun auditWithOverrides() {
    assertEquals(0, auditClient.sentEvents.size)
    assertThat(
        webTestClient
          .call("/call/withOverrides") { post("hello".toRequestBody(MediaTypes.APPLICATION_JSON_MEDIA_TYPE)) }
          .response
          .isSuccessful
      )
      .isTrue()

    assertEquals(
      FakeAuditClient.FakeAuditEvent(
        eventSource = "test",
        eventTarget = "override-target",
        timestampSent = 2147483647,
        applicationName = "override-application-name",
        approverLDAP = null,
        automatedChange = true,
        description = "override-description",
        richDescription =
          "override-rich-description AuditRequestActionWithOverrides principal=unknown time=100.0 ms code=200",
        environment = "testing",
        detailURL = "override-detail-url",
        region = "us-west-2",
        requestorLDAP = null,
      ),
      auditClient.sentEvents.take(),
    )
  }

  @Test
  fun `requestResponseBodyTransformer applies all relevant transformers`() {
    assertThat(invoke("/call/logEverything/Quokka", "caller").isSuccessful).isTrue()
  }

  @Test
  fun `requestResponseBodyTransformer contains explosions`() {
    assertThat(invoke("/call/logEverything/Oppenheimer-the-bestest", "test-user").isSuccessful).isTrue()
  }

  fun invoke(path: String, asUser: String? = null): Response {
    val url = jettyService.httpServerUrl.newBuilder().encodedPath(path).build()

    val request = Request.Builder().url(url).get()
    asUser?.let { request.addHeader(FakeCallerAuthenticator.USER_HEADER, it) }
    return httpClient.newCall(request.build()).execute()
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AppNameModule("test"))
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(TestActionsModule())

      install(FakeAuditClientModule())

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
      install(WebActionModule.create<AuditRequestActionWithHeaders>())
      install(WebActionModule.create<AuditRequestActionWithOverrides>())
    }
  }
}

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
  @AuditRequestResponse(includeRequest = true, includeResponse = true)
  fun call(@PathParam message: String) = "echo: $message"
}

internal class AuditExceptionThrowingRequestAction @Inject constructor() : WebAction {
  @Get("/call/exceptionThrowingRequestLogging/{message}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AuditRequestResponse
  fun call(@PathParam message: String): String = throw IllegalStateException(message)
}

internal class AuditNoRequestAction @Inject constructor() : WebAction {
  @Get("/call/noRequestLogging/{message}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call(@PathParam message: String) = "echo: $message"
}

internal class AuditRequestActionWithHeaders @Inject constructor() : WebAction {
  @Post("/call/withHeaders")
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AuditRequestResponse(
    includeRequest = true,
    includeRequestHeaders = true,
    includeResponse = true,
    includeReseponseHeaders = true,
  )
  fun call(@RequestBody message: String, @RequestHeaders headers: Headers): String {
    if (message == "fail") throw BadRequestException(message = "boom")
    return "echo: $message"
  }
}

internal class AuditRequestActionWithOverrides @Inject constructor() : WebAction {
  @Post("/call/withOverrides")
  @Unauthenticated
  @AuditRequestResponse(
    target = "override-target",
    description = "override-description",
    automatedChange = true,
    richDescription = "override-rich-description",
    detailURL = "override-detail-url",
    applicationName = "override-application-name",
  )
  fun call(): String = "override world"
}
