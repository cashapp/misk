package misk.audit

import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.servlet.http.HttpServletRequest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import misk.Action
import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.exceptions.BadRequestException
import misk.inject.ReusableTestModule
import misk.logging.LogCollector
import misk.logging.LogCollectorModule
import misk.scope.ActionScope
import misk.scope.ActionScoped
import misk.security.authz.AccessControlModule
import misk.security.authz.Authenticated
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.testing.FakeFixture
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestFixture
import misk.time.FakeClock
import misk.web.Get
import misk.web.HttpCall
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.PathParam
import misk.web.RequestHeaders
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.interceptors.BeforeContentEncoding
import misk.web.interceptors.RequestResponseBody
import misk.web.interceptors.hooks.RequestResponseHook
import okhttp3.Headers
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class FakeAuditRequestCaptureTest {
  @MiskTestModule val module = TestModule()
  @Inject private lateinit var auditClient: FakeAuditClient
  @Inject private lateinit var webTestClient: WebTestClient
  @Inject private lateinit var requestControls: RequestControls
  @Inject private lateinit var auditBindings: FakeAuditClient.OptionalBinder
  @Inject private lateinit var logCollector: LogCollector

  @Test
  fun `completion timeout returns while fake recording is blocked`() {
    val recordingGate = Gate()
    val originalClock = auditBindings.clock
    auditClient.captureHttpRequest().use { capture ->
      try {
        auditBindings.clock =
          object : FakeClock() {
            override fun instant(): Instant {
              recordingGate.block()
              return super.instant()
            }
          }
        callWithCapture("/audited/blocked-recording", capture)
        recordingGate.awaitEntered()
        val timeoutFailure =
          CompletableFuture.supplyAsync { catchThrowable { capture.awaitCompleted(Duration.ofMillis(50)) } }
        assertThat(timeoutFailure.get(1, SECONDS))
          .isInstanceOf(TimeoutException::class.java)
          .hasMessageContaining("IN_FLIGHT")
      } finally {
        recordingGate.release.countDown()
        auditBindings.clock = originalClock
      }
      assertThat(capture.awaitCompleted(TIMEOUT).events).hasSize(1)
    }
  }

  @Test
  fun `completion waits past response close and legacy consumption for synchronous hooks`() {
    val hookGate = Gate()
    requestControls.hookGates["one"] = hookGate
    auditClient.captureHttpRequest().use { capture ->
      try {
        callWithCapture("/audited/one", capture)
        hookGate.awaitEntered()
        assertThat(auditClient.sentEvents).hasSize(1)
        val legacyEvent = auditClient.sentEvents.take()
        assertThat(legacyEvent.requestorLDAP).isEqualTo("test-user")
        assertThat(legacyEvent.richDescription).contains("principal=test-user")
        val hookHeaders = requestControls.hookHeaders.getValue("one")
        assertThat(hookHeaders[AuditRequestCaptures.CORRELATION_HEADER]).isNull()
        assertThat(hookHeaders[FakeCallerAuthenticator.USER_HEADER]).isEqualTo("test-user")
        assertThatThrownBy { capture.awaitCompleted(Duration.ZERO) }
          .isInstanceOf(TimeoutException::class.java)
          .hasMessageContaining("IN_FLIGHT")
        hookGate.release.countDown()
        val completedRequest = capture.awaitCompleted(TIMEOUT)
        assertThat(completedRequest.statusCode).isEqualTo(200)
        assertThat(completedRequest.events).containsExactly(legacyEvent)
        assertThat(completedRequest.events.single().eventTarget).isEqualTo("FakeAuditRequestCaptureTest.AuditedAction")
        assertThat(capture.awaitCompleted(TIMEOUT)).isSameAs(completedRequest)
        auditClient.sentEvents.clear()
        assertThat(completedRequest.events).containsExactly(legacyEvent)
        assertThatThrownBy { (completedRequest.events as MutableList).clear() }
          .isInstanceOf(UnsupportedOperationException::class.java)
      } finally {
        hookGate.release.countDown()
      }
    }
  }

  @Test
  fun `uncaptured HTTP request still records to the legacy queue`() {
    val hookGate = Gate()
    requestControls.hookGates["uncaptured"] = hookGate
    try {
      webTestClient
        .call("/audited/uncaptured") { header(FakeCallerAuthenticator.USER_HEADER, "test-user") }
        .response
        .use { assertThat(it.code).isEqualTo(200) }
      hookGate.awaitEntered()
      assertThat(auditClient.sentEvents).hasSize(1)
      val legacyEvent = auditClient.sentEvents.take()
      assertThat(legacyEvent.eventTarget).isEqualTo("FakeAuditRequestCaptureTest.AuditedAction")
      assertThat(legacyEvent.requestorLDAP).isEqualTo("test-user")
    } finally {
      hookGate.release.countDown()
    }
  }

  @Test
  fun `capture can be applied before the request URL is configured`() {
    auditClient.captureHttpRequest().use { capture ->
      val requestBuilder = Request.Builder()
      capture.applyTo(requestBuilder)
      webTestClient
        .call("/plain") {
          headers(requestBuilder.url(build().url).build().headers)
          header(FakeCallerAuthenticator.USER_HEADER, "test-user")
        }
        .response
        .use { assertThat(it.code).isEqualTo(200) }
      assertThat(capture.awaitCompleted(TIMEOUT).events).isEmpty()
    }
  }

  @Test
  fun `raw servlet request hides capture header and preserves ordinary headers`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/servlet-headers", capture)
      assertThat(capture.awaitCompleted(TIMEOUT).events).isEmpty()
    }
  }

  @Test
  fun `unannotated request waits for chain exit before returning zero recordings`() {
    val plainGate = Gate()
    requestControls.plainGate = plainGate
    auditClient.captureHttpRequest().use { capture ->
      try {
        callWithCapture("/plain", capture)
        plainGate.awaitEntered()
        assertThatThrownBy { capture.awaitCompleted(Duration.ZERO) }.isInstanceOf(TimeoutException::class.java)
        plainGate.release.countDown()
        assertThat(capture.awaitCompleted(TIMEOUT).events).isEmpty()
      } finally {
        plainGate.release.countDown()
      }
    }
  }

  @Test
  fun `unannotated action can record directly`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/direct", capture)
      assertThat(capture.awaitCompleted(TIMEOUT).events.single().description).isEqualTo("direct recording")
    }
  }

  @Test
  fun `delayed second producer is included and single assertion detects duplicate`() {
    val producerGate = Gate { auditClient.logEvent("second", "second recording") }
    requestControls.hookGates["duplicate"] = producerGate
    auditClient.captureHttpRequest().use { capture ->
      try {
        callWithCapture("/audited/duplicate", capture)
        producerGate.awaitEntered()
        assertThat(auditClient.sentEvents).hasSize(1)
        assertThat(auditClient.sentEvents.take().eventTarget).isEqualTo("FakeAuditRequestCaptureTest.AuditedAction")
        producerGate.release.countDown()
        val completedRequest = capture.awaitCompleted(TIMEOUT)
        assertThat(completedRequest.events.map { it.eventTarget })
          .containsExactly("FakeAuditRequestCaptureTest.AuditedAction", "second")
        assertThatThrownBy { completedRequest.events.single() }.isInstanceOf(IllegalArgumentException::class.java)
      } finally {
        producerGate.release.countDown()
      }
    }
  }

  @Test
  fun `same action requests have independent completion and recordings`() {
    val firstGate = Gate()
    val secondGate = Gate()
    requestControls.hookGates["first"] = firstGate
    requestControls.hookGates["second"] = secondGate
    auditClient.captureHttpRequest().use { firstCapture ->
      auditClient.captureHttpRequest().use { secondCapture ->
        try {
          callWithCapture("/audited/first", firstCapture)
          callWithCapture("/audited/second", secondCapture)
          firstGate.awaitEntered()
          secondGate.awaitEntered()
          secondGate.release.countDown()
          val secondRequest = secondCapture.awaitCompleted(TIMEOUT)
          assertThat(secondRequest.events.single().richDescription).contains("response=second")
          assertThatThrownBy { firstCapture.awaitCompleted(Duration.ZERO) }.isInstanceOf(TimeoutException::class.java)
          firstGate.release.countDown()
          assertThat(firstCapture.awaitCompleted(TIMEOUT).events.single().richDescription).contains("response=first")
        } finally {
          firstGate.release.countDown()
          secondGate.release.countDown()
        }
      }
    }
  }

  @Test
  fun `joined scope propagated recording belongs to its request`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/joined", capture)
      assertThat(capture.awaitCompleted(TIMEOUT).events.single().eventTarget).isEqualTo("joined")
    }
  }

  @Test
  fun `mapped handler failure retains final status and actual recording`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/audited/mapped", capture, expectedStatus = 400)
      assertThat(capture.awaitCompleted(TIMEOUT).statusCode).isEqualTo(400)
      assertThat(capture.awaitCompleted(TIMEOUT).events).hasSize(1)
    }
  }

  @Test
  fun `unmapped handler failure retains final status and actual recording`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/audited/unmapped", capture, expectedStatus = 500)
      assertThat(capture.awaitCompleted(TIMEOUT).statusCode).isEqualTo(500)
      assertThat(capture.awaitCompleted(TIMEOUT).events).hasSize(1)
    }
  }

  @Test
  fun `authentication is still enforced`() {
    auditClient.captureHttpRequest().use { capture ->
      webTestClient
        .call("/audited/denied") { capture.applyTo(this) }
        .response
        .use { assertThat(it.code).isEqualTo(401) }
      assertThat(capture.awaitCompleted(TIMEOUT).statusCode).isEqualTo(401)
    }
  }

  @Test
  fun `escaping dispatch failure fails capture with original cause`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/escape", capture, expectedStatus = 500)
      assertThatThrownBy { capture.awaitCompleted(TIMEOUT) }
        .isInstanceOf(ExecutionException::class.java)
        .hasCause(requestControls.dispatchFailure)
    }
  }

  @Test
  fun `swallowed hook failure before fake is an empty actual recording snapshot`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/audited/hook-failure", capture)
      assertThat(capture.awaitCompleted(TIMEOUT).events).isEmpty()
    }
  }

  @Test
  fun `fake construction failure is retained even when hook runner swallows it`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/audited/recording-failure", capture)
      assertThatThrownBy { capture.awaitCompleted(TIMEOUT) }
        .isInstanceOf(ExecutionException::class.java)
        .hasRootCauseInstanceOf(ArithmeticException::class.java)
    }
  }

  @Test
  fun `late scoped recording fails without changing completed snapshot`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/late", capture)
      val completedRequest = capture.awaitCompleted(TIMEOUT)
      assertThatThrownBy { requestControls.lateRecording!!() }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("after request completion")
      assertThat(completedRequest.events).isEmpty()
      assertThat(auditClient.sentEvents).isEmpty()
    }
  }

  @Test
  fun `reset rejects stale propagated writes and direct legacy logging still works`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/late", capture)
      capture.awaitCompleted(TIMEOUT)
    }
    auditClient.reset()
    assertThatThrownBy { requestControls.lateRecording!!() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("fixture reset")
    auditClient.logEvent("direct", "outside HTTP")
    assertThat(auditClient.sentEvents).hasSize(1)
    assertThat(auditClient.sentEvents.take().description).isEqualTo("outside HTTP")
  }

  @Test
  fun `reset refuses running capture even after close and permits injector reuse after exit`() {
    val hookGate = Gate()
    requestControls.hookGates["reset"] = hookGate
    val capture = auditClient.captureHttpRequest()
    try {
      callWithCapture("/audited/reset", capture)
      hookGate.awaitEntered()
      capture.close()
      assertThatThrownBy { auditClient.reset() }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("IN_FLIGHT")
    } finally {
      hookGate.release.countDown()
    }
    check(requestControls.resetChainExited.await(10, SECONDS)) { "Abandoned chain did not exit" }
    auditClient.reset()
    auditClient.captureHttpRequest().use { nextCapture ->
      callWithCapture("/plain", nextCapture)
      assertThat(nextCapture.awaitCompleted(TIMEOUT).events).isEmpty()
      assertThat(auditClient.sentEvents).isEmpty()
    }
  }

  @Test
  fun `action construction failure never fabricates an empty completed request`() {
    auditClient.captureHttpRequest().use { capture ->
      callWithCapture("/construction-failure", capture, expectedStatus = 500)
      assertThatThrownBy { capture.awaitCompleted(Duration.ZERO) }
        .isInstanceOf(TimeoutException::class.java)
        .hasMessageContaining("NOT_OBSERVED")
    }
  }

  @Test
  fun `unobserved request times out without an empty completed snapshot`() {
    auditClient.captureHttpRequest().use { capture ->
      assertThatThrownBy { capture.awaitCompleted(Duration.ZERO) }
        .isInstanceOf(TimeoutException::class.java)
        .hasMessageContaining("NOT_OBSERVED")
    }
  }

  @Test
  fun `closing an unsent capture fails its completion`() {
    auditClient.captureHttpRequest().use { capture ->
      capture.close()
      assertThatThrownBy { capture.awaitCompleted(TIMEOUT) }
        .isInstanceOf(ExecutionException::class.java)
        .hasMessageContaining("closed")
    }
  }

  @Test
  fun `reset cancels an unsent capture and prevents applying it`() {
    val resetCapture = auditClient.captureHttpRequest()
    auditClient.reset()
    assertThatThrownBy { resetCapture.awaitCompleted(TIMEOUT) }
      .isInstanceOf(ExecutionException::class.java)
      .hasMessageContaining("reset")
    assertThatThrownBy { resetCapture.applyTo(Request.Builder()) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("closed or reset")
  }

  @Test
  fun `unknown correlation ID is rejected`() {
    webTestClient
      .call("/plain") { addHeader(AuditRequestCaptures.CORRELATION_HEADER, "unknown") }
      .response
      .use { assertThat(it.code).isEqualTo(500) }
    assertThat(logCollector.takeEvents().map { it.throwableProxy?.message })
      .contains("Unknown, reset or previously used audit capture ID")
  }

  @Test
  fun `duplicate correlation IDs are rejected`() {
    auditClient.captureHttpRequest().use { capture ->
      webTestClient
        .call("/plain") {
          capture.applyTo(this)
          addHeader(AuditRequestCaptures.CORRELATION_HEADER, "duplicate")
        }
        .response
        .use { assertThat(it.code).isEqualTo(500) }
      assertThat(logCollector.takeEvents().map { it.throwableProxy?.message })
        .contains("Duplicate audit capture headers")
    }
  }

  @Test
  fun `capture and its correlation ID cannot be reused`() {
    auditClient.captureHttpRequest().use { capture ->
      lateinit var capturedRequest: Request
      webTestClient
        .call("/plain") {
          capture.applyTo(this)
          header(FakeCallerAuthenticator.USER_HEADER, "test-user")
          capturedRequest = build()
        }
        .response
        .use { assertThat(it.code).isEqualTo(200) }
      capture.awaitCompleted(TIMEOUT)
      assertThatThrownBy { capture.applyTo(capturedRequest.newBuilder()) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("only be applied once")
      webTestClient
        .call("/plain") { headers(capturedRequest.headers) }
        .response
        .use { assertThat(it.code).isEqualTo(500) }
      assertThat(logCollector.takeEvents().map { it.throwableProxy?.message })
        .contains("Audit capture ID already used or closed; retries and redirects are unsupported")
    }
  }

  @Test
  fun `correlation ID from a reset fixture is rejected`() {
    auditClient.captureHttpRequest().use { capture ->
      lateinit var capturedRequest: Request
      webTestClient
        .call("/plain") {
          capture.applyTo(this)
          header(FakeCallerAuthenticator.USER_HEADER, "test-user")
          capturedRequest = build()
        }
        .response
        .use { assertThat(it.code).isEqualTo(200) }
      capture.awaitCompleted(TIMEOUT)
      auditClient.reset()
      webTestClient
        .call("/plain") { headers(capturedRequest.headers) }
        .response
        .use { assertThat(it.code).isEqualTo(500) }
      assertThat(logCollector.takeEvents().map { it.throwableProxy?.message })
        .contains("Unknown, reset or previously used audit capture ID")
    }
  }

  private fun callWithCapture(path: String, capture: FakeAuditRequestCapture, expectedStatus: Int = 200) {
    webTestClient
      .call(path) {
        capture.applyTo(this)
        header(FakeCallerAuthenticator.USER_HEADER, "test-user")
      }
      .response
      .use { response ->
        assertThat(response.code).isEqualTo(expectedStatus)
        response.body.string()
      }
  }

  class TestModule : ReusableTestModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(LogCollectorModule())
      multibind<TestFixture>().to<RequestControls>()
      install(AccessControlModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      multibind<NetworkInterceptor.Factory>(BeforeContentEncoding::class).to<ResetChainExitFactory>()
      multibind<RequestResponseHook.Factory>().to<BeforeAuditHookFactory>()
      install(FakeAuditClientModule())
      install(FakeAuditRequestCompletionModule())
      multibind<RequestResponseHook.Factory>().to<GateHookFactory>()
      multibind<NetworkInterceptor.Factory>(BeforeContentEncoding::class).to<EscapingInterceptorFactory>()
      multibind<NetworkInterceptor.Factory>(BeforeContentEncoding::class).to<PlainChainGateFactory>()
      install(WebActionModule.create<AuditedAction>())
      install(WebActionModule.create<PlainAction>())
      install(WebActionModule.create<ServletHeadersAction>())
      install(WebActionModule.create<DirectRecordingAction>())
      install(WebActionModule.create<JoinedRecordingAction>())
      install(WebActionModule.create<LateRecordingAction>())
      install(WebActionModule.create<EscapeAction>())
      install(WebActionModule.create<ConstructionFailureAction>())
    }
  }

  class Gate(private val afterRelease: () -> Unit = {}) {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    fun awaitEntered() {
      check(entered.await(10, SECONDS)) { "Request did not reach gate" }
    }

    fun block() {
      entered.countDown()
      check(release.await(10, SECONDS)) { "Request gate was not released" }
      afterRelease()
    }
  }

  @Singleton
  class RequestControls @Inject constructor() : FakeFixture() {
    val hookGates by resettable { ConcurrentHashMap<String, Gate>() }
    val hookHeaders by resettable { ConcurrentHashMap<String, Headers>() }
    val resetChainExited by resettable { CountDownLatch(1) }
    val dispatchFailure = IllegalStateException("uncaught dispatch failure")
    var lateRecording by resettable<(() -> Unit)?> { null }
    var plainGate by resettable<Gate?> { null }
  }

  class BeforeAuditHookFactory @Inject constructor(private val auditClient: AuditClient) : RequestResponseHook.Factory {
    override fun create(action: Action): RequestResponseHook? =
      if (action.name == "FakeAuditRequestCaptureTest.AuditedAction")
        hook { httpCall ->
          check(httpCall.url.pathSegments.last() != "hook-failure") { "failure before fake" }
          if (httpCall.url.pathSegments.last() == "recording-failure") {
            auditClient.logEvent("failure", "failure", timestampSent = Instant.MAX)
          }
        }
      else null
  }

  class GateHookFactory @Inject constructor(private val requestControls: RequestControls) :
    RequestResponseHook.Factory {
    override fun create(action: Action): RequestResponseHook? =
      if (action.name == "FakeAuditRequestCaptureTest.AuditedAction")
        hook { httpCall ->
          val requestMessage = httpCall.url.pathSegments.last()
          requestControls.hookHeaders[requestMessage] = httpCall.requestHeaders
          requestControls.hookGates[requestMessage]?.block()
        }
      else null
  }

  class ResetChainExitFactory @Inject constructor(private val requestControls: RequestControls) :
    NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor =
      object : NetworkInterceptor {
        override fun intercept(chain: NetworkChain) {
          try {
            chain.proceed(chain.httpCall)
          } finally {
            if (chain.httpCall.url.pathSegments.last() == "reset") requestControls.resetChainExited.countDown()
          }
        }
      }
  }

  class EscapingInterceptorFactory @Inject constructor(private val requestControls: RequestControls) :
    NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor =
      object : NetworkInterceptor {
        override fun intercept(chain: NetworkChain) {
          if (action.name == "FakeAuditRequestCaptureTest.EscapeAction") throw requestControls.dispatchFailure
          chain.proceed(chain.httpCall)
        }
      }
  }

  class PlainChainGateFactory @Inject constructor(private val requestControls: RequestControls) :
    NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? =
      if (action.name == "FakeAuditRequestCaptureTest.PlainAction")
        object : NetworkInterceptor {
          override fun intercept(chain: NetworkChain) {
            chain.proceed(chain.httpCall)
            requestControls.plainGate?.block()
          }
        }
      else null
  }

  class AuditedAction @Inject constructor() : WebAction {
    @Get("/audited/{message}")
    @Authenticated(allowAnyUser = true)
    @AuditRequestResponse(includeRequestHeaders = true, includeResponse = true)
    fun call(@PathParam message: String, @RequestHeaders headers: Headers): String {
      check(headers[AuditRequestCaptures.CORRELATION_HEADER] == null)
      if (message == "mapped") throw BadRequestException("mapped failure")
      if (message == "unmapped") error("unmapped failure")
      return message
    }
  }

  class PlainAction @Inject constructor() : WebAction {
    @Get("/plain") @Authenticated(allowAnyUser = true) fun call() = "plain"
  }

  class ServletHeadersAction @Inject constructor(private val servletRequest: ActionScoped<HttpServletRequest>) :
    WebAction {
    @Get("/servlet-headers")
    @Authenticated(allowAnyUser = true)
    fun call(): String {
      val scopedRequest = servletRequest.get()
      val captureHeader = AuditRequestCaptures.CORRELATION_HEADER.lowercase()
      check(scopedRequest.getHeader(captureHeader) == null)
      check(scopedRequest.getHeaders(captureHeader).toList().isEmpty())
      check(scopedRequest.headerNames.toList().none { it.equals(captureHeader, ignoreCase = true) })
      check(scopedRequest.getIntHeader(captureHeader) == -1)
      check(scopedRequest.getDateHeader(captureHeader) == -1L)
      check(scopedRequest.getHeader(FakeCallerAuthenticator.USER_HEADER) == "test-user")
      return "headers checked"
    }
  }

  class DirectRecordingAction @Inject constructor(private val auditClient: AuditClient) : WebAction {
    @Get("/direct")
    @Authenticated(allowAnyUser = true)
    fun call(): String {
      auditClient.logEvent("direct", "direct recording")
      return "direct"
    }
  }

  class JoinedRecordingAction
  @Inject
  constructor(private val auditClient: AuditClient, private val scope: ActionScope) : WebAction {
    @Get("/joined")
    @Authenticated(allowAnyUser = true)
    fun call(): String {
      CompletableFuture.runAsync(scope.propagate { auditClient.logEvent("joined", "joined recording") })
        .get(10, SECONDS)
      return "joined"
    }
  }

  class LateRecordingAction
  @Inject
  constructor(
    private val auditClient: AuditClient,
    private val scope: ActionScope,
    private val requestControls: RequestControls,
  ) : WebAction {
    @Get("/late")
    @Authenticated(allowAnyUser = true)
    fun call(): String {
      requestControls.lateRecording = scope.propagate { auditClient.logEvent("late", "late recording") }
      return "late"
    }
  }

  class ConstructionFailureAction @Inject constructor() : WebAction {
    init {
      error("Action construction failed before network chain")
    }

    @Get("/construction-failure") @Authenticated(allowAnyUser = true) fun call() = "unreachable"
  }

  class EscapeAction @Inject constructor() : WebAction {
    @Get("/escape") @Authenticated(allowAnyUser = true) fun call() = "unreachable"
  }

  companion object {
    private val TIMEOUT = Duration.ofSeconds(10)

    private fun hook(handleCall: (HttpCall) -> Unit) =
      object : RequestResponseHook {
        override fun handle(
          caller: MiskCaller?,
          httpCall: HttpCall,
          requestResponse: RequestResponseBody?,
          elapsed: Duration,
          elapsedToString: String,
          error: Throwable?,
        ) {
          handleCall(httpCall)
        }
      }
  }
}
