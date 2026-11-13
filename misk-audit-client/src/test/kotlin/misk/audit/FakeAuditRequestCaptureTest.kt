package misk.audit

import jakarta.inject.Inject
import jakarta.inject.Singleton
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
import misk.scope.ActionScope
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
  @Inject private lateinit var controls: RequestControls
  @Inject private lateinit var auditBindings: FakeAuditClient.OptionalBinder

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
        call("/audited/blocked-recording", capture)
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
  fun `completion waits past response close and legacy take for synchronous hooks`() {
    val hookGate = Gate()
    controls.hookGates["one"] = hookGate
    auditClient.captureHttpRequest().use { capture ->
      try {
        call("/audited/one", capture)
        hookGate.awaitEntered()
        val legacyEvent = auditClient.sentEvents.take()
        assertThat(legacyEvent.requestorLDAP).isEqualTo("test-user")
        assertThat(legacyEvent.richDescription)
          .contains("principal=test-user")
          .doesNotContain(AuditRequestCaptures.HEADER.lowercase())
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
  fun `unannotated request waits for chain exit before returning zero recordings`() {
    val plainGate = Gate()
    controls.plainGate = plainGate
    auditClient.captureHttpRequest().use { capture ->
      try {
        call("/plain", capture)
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
      call("/direct", capture)
      assertThat(capture.awaitCompleted(TIMEOUT).events.single().description).isEqualTo("direct recording")
    }
  }

  @Test
  fun `delayed second producer is included and single assertion detects duplicate`() {
    val producerGate = Gate { auditClient.logEvent("second", "second recording") }
    controls.hookGates["duplicate"] = producerGate
    auditClient.captureHttpRequest().use { capture ->
      try {
        call("/audited/duplicate", capture)
        producerGate.awaitEntered()
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
    controls.hookGates["first"] = firstGate
    controls.hookGates["second"] = secondGate
    auditClient.captureHttpRequest().use { firstCapture ->
      auditClient.captureHttpRequest().use { secondCapture ->
        try {
          call("/audited/first", firstCapture)
          call("/audited/second", secondCapture)
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
      call("/joined", capture)
      assertThat(capture.awaitCompleted(TIMEOUT).events.single().eventTarget).isEqualTo("joined")
    }
  }

  @Test
  fun `mapped handler failure retains final status and actual recording`() {
    auditClient.captureHttpRequest().use { capture ->
      call("/audited/mapped", capture, expectedStatus = 400)
      assertThat(capture.awaitCompleted(TIMEOUT).statusCode).isEqualTo(400)
      assertThat(capture.awaitCompleted(TIMEOUT).events).hasSize(1)
    }
  }

  @Test
  fun `unmapped handler failure retains final status and actual recording`() {
    auditClient.captureHttpRequest().use { capture ->
      call("/audited/unmapped", capture, expectedStatus = 500)
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
      call("/escape", capture, expectedStatus = 500)
      assertThatThrownBy { capture.awaitCompleted(TIMEOUT) }
        .isInstanceOf(ExecutionException::class.java)
        .hasCause(controls.dispatchFailure)
    }
  }

  @Test
  fun `swallowed hook failure before fake is an empty actual recording snapshot`() {
    auditClient.captureHttpRequest().use { capture ->
      call("/audited/hook-failure", capture)
      assertThat(capture.awaitCompleted(TIMEOUT).events).isEmpty()
    }
  }

  @Test
  fun `fake construction failure is retained even when hook runner swallows it`() {
    auditClient.captureHttpRequest().use { capture ->
      call("/audited/recording-failure", capture)
      assertThatThrownBy { capture.awaitCompleted(TIMEOUT) }
        .isInstanceOf(ExecutionException::class.java)
        .hasRootCauseInstanceOf(ArithmeticException::class.java)
    }
  }

  @Test
  fun `late scoped recording fails without changing completed snapshot`() {
    auditClient.captureHttpRequest().use { capture ->
      call("/late", capture)
      val completedRequest = capture.awaitCompleted(TIMEOUT)
      assertThatThrownBy { controls.lateRecording!!() }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("after request completion")
      assertThat(completedRequest.events).isEmpty()
      assertThat(auditClient.sentEvents).isEmpty()
    }
  }

  @Test
  fun `reset rejects stale propagated writes and direct legacy logging still works`() {
    auditClient.captureHttpRequest().use { capture ->
      call("/late", capture)
      capture.awaitCompleted(TIMEOUT)
    }
    auditClient.reset()
    assertThatThrownBy { controls.lateRecording!!() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("fixture reset")
    auditClient.logEvent("direct", "outside HTTP")
    assertThat(auditClient.sentEvents.take().description).isEqualTo("outside HTTP")
  }

  @Test
  fun `reset refuses running capture even after close and permits injector reuse after exit`() {
    val hookGate = Gate()
    controls.hookGates["reset"] = hookGate
    val capture = auditClient.captureHttpRequest()
    try {
      call("/audited/reset", capture)
      hookGate.awaitEntered()
      capture.close()
      assertThatThrownBy { auditClient.reset() }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("IN_FLIGHT")
    } finally {
      hookGate.release.countDown()
    }
    check(controls.chainExited.await(10, SECONDS)) { "Abandoned chain did not exit" }
    auditClient.reset()
    auditClient.captureHttpRequest().use { nextCapture ->
      call("/plain", nextCapture)
      assertThat(nextCapture.awaitCompleted(TIMEOUT).events).isEmpty()
      assertThat(auditClient.sentEvents).isEmpty()
    }
  }

  @Test
  fun `action construction failure never fabricates an empty completed request`() {
    auditClient.captureHttpRequest().use { capture ->
      call("/construction-failure", capture, expectedStatus = 500)
      assertThatThrownBy { capture.awaitCompleted(Duration.ZERO) }
        .isInstanceOf(TimeoutException::class.java)
        .hasMessageContaining("NOT_OBSERVED")
    }
  }

  @Test
  fun `unobserved requests and cancelled unsent registrations fail diagnostically`() {
    auditClient.captureHttpRequest().use { capture ->
      assertThatThrownBy { capture.awaitCompleted(Duration.ZERO) }
        .isInstanceOf(TimeoutException::class.java)
        .hasMessageContaining("NOT_OBSERVED")
      capture.close()
      assertThatThrownBy { capture.awaitCompleted(TIMEOUT) }
        .isInstanceOf(ExecutionException::class.java)
        .hasMessageContaining("closed")
    }
    val resetCapture = auditClient.captureHttpRequest()
    auditClient.reset()
    assertThatThrownBy { resetCapture.awaitCompleted(TIMEOUT) }
      .isInstanceOf(ExecutionException::class.java)
      .hasMessageContaining("reset")
    assertThatThrownBy { resetCapture.applyTo(Request.Builder().url("http://localhost/")) }
      .isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun `unknown duplicate reused and reset correlation IDs are rejected`() {
    webTestClient
      .call("/plain") { addHeader(AuditRequestCaptures.HEADER, "unknown") }
      .response
      .use { assertThat(it.code).isEqualTo(500) }
    auditClient.captureHttpRequest().use { capture ->
      lateinit var capturedRequest: Request
      webTestClient
        .call("/plain") {
          capture.applyTo(this)
          capturedRequest = build()
        }
        .response
        .close()
      capture.awaitCompleted(TIMEOUT)
      assertThatThrownBy { capture.applyTo(capturedRequest.newBuilder()) }
        .isInstanceOf(IllegalStateException::class.java)
      webTestClient
        .call("/plain") { headers(capturedRequest.headers) }
        .response
        .use { assertThat(it.code).isEqualTo(500) }
      auditClient.reset()
      webTestClient
        .call("/plain") { headers(capturedRequest.headers) }
        .response
        .use { assertThat(it.code).isEqualTo(500) }
    }
    auditClient.captureHttpRequest().use { duplicateCapture ->
      webTestClient
        .call("/plain") {
          duplicateCapture.applyTo(this)
          addHeader(AuditRequestCaptures.HEADER, "duplicate")
        }
        .response
        .use { assertThat(it.code).isEqualTo(500) }
    }
  }

  private fun call(path: String, capture: FakeAuditRequestCapture, expectedStatus: Int = 200) {
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
      multibind<TestFixture>().to<RequestControls>()
      install(AccessControlModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      multibind<NetworkInterceptor.Factory>(BeforeContentEncoding::class).to<ChainExitFactory>()
      multibind<RequestResponseHook.Factory>().to<BeforeAuditHookFactory>()
      install(FakeAuditClientModule())
      install(FakeAuditRequestCompletionModule())
      multibind<RequestResponseHook.Factory>().to<GateHookFactory>()
      multibind<NetworkInterceptor.Factory>(BeforeContentEncoding::class).to<EscapingInterceptorFactory>()
      install(WebActionModule.create<AuditedAction>())
      install(WebActionModule.create<PlainAction>())
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
    val chainExited by resettable { CountDownLatch(1) }
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

  class GateHookFactory @Inject constructor(private val controls: RequestControls) : RequestResponseHook.Factory {
    override fun create(action: Action): RequestResponseHook? =
      if (action.name == "FakeAuditRequestCaptureTest.AuditedAction")
        hook { httpCall -> controls.hookGates[httpCall.url.pathSegments.last()]?.block() }
      else null
  }

  class ChainExitFactory @Inject constructor(private val controls: RequestControls) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor =
      object : NetworkInterceptor {
        override fun intercept(chain: NetworkChain) {
          try {
            chain.proceed(chain.httpCall)
          } finally {
            if (chain.httpCall.url.pathSegments.last() == "reset") controls.chainExited.countDown()
          }
        }
      }
  }

  class EscapingInterceptorFactory @Inject constructor(private val controls: RequestControls) :
    NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor =
      object : NetworkInterceptor {
        override fun intercept(chain: NetworkChain) {
          if (action.name == "FakeAuditRequestCaptureTest.EscapeAction") throw controls.dispatchFailure
          chain.proceed(chain.httpCall)
          if (action.name == "FakeAuditRequestCaptureTest.PlainAction") controls.plainGate?.block()
        }
      }
  }

  class AuditedAction @Inject constructor() : WebAction {
    @Get("/audited/{message}")
    @Authenticated(allowAnyUser = true)
    @AuditRequestResponse(includeRequestHeaders = true, includeResponse = true)
    fun call(@PathParam message: String, @RequestHeaders headers: Headers): String {
      check(headers[AuditRequestCaptures.HEADER] == null)
      if (message == "mapped") throw BadRequestException("mapped failure")
      if (message == "unmapped") error("unmapped failure")
      return message
    }
  }

  class PlainAction @Inject constructor() : WebAction {
    @Get("/plain") @Authenticated(allowAnyUser = true) fun call() = "plain"
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
    private val controls: RequestControls,
  ) : WebAction {
    @Get("/late")
    @Authenticated(allowAnyUser = true)
    fun call(): String {
      controls.lateRecording = scope.propagate { auditClient.logEvent("late", "late recording") }
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
