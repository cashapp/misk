package misk.web.interceptors

import jakarta.inject.Inject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import misk.Action
import misk.MiskTestingServiceModule
import misk.asAction
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.DispatchMechanism
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealNetworkChain
import misk.web.RequestDeadlineMode
import misk.web.RequestDeadlineTimeout
import misk.web.RequestDeadlinesConfig
import misk.web.ServletHttpCall
import misk.web.WebConfig
import misk.web.actions.WebAction
import misk.web.actions.WebSocketListener
import misk.web.interceptors.RequestDeadlineInterceptor.Companion.MISK_REQUEST_DEADLINE_HEADER
import misk.web.requestdeadlines.RequestDeadlineMetrics
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class RequestDeadlineInterceptorTest {
  @MiskTestModule val module = TestModule()

  @Inject private lateinit var requestDeadlineInterceptorFactory: RequestDeadlineInterceptor.Factory
  @Inject private lateinit var clock: Clock
  @Inject private lateinit var requestDeadlineMetrics: RequestDeadlineMetrics

  @Test
  fun `no timeout when no headers and no annotation`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall()
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Verify that the default 10-second timeout was applied
    assertDeadlineHeaderTimeout(httpCall, 10000)
  }

  @Test
  fun `timeout from annotation is used`() {
    val action = AnnotationTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall()
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Verify that the deadline header was added with a deadline approximately 5 seconds from now
    assertDeadlineHeaderTimeout(httpCall, 5000)
  }

  @Test
  fun `timeout from X-Request-Deadline header in seconds`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall(requestHeaders = headersOf("x-request-deadline", "15"))
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Verify that the deadline header was added with a deadline approximately 15 seconds from now
    assertDeadlineHeaderTimeout(httpCall, 15000)
  }

  @Test
  fun `timeout from x-envoy-expected-rq-timeout-ms header`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall(requestHeaders = headersOf("x-envoy-expected-rq-timeout-ms", "3000"))
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Verify that the deadline header was added with a deadline approximately 3 seconds from now
    assertDeadlineHeaderTimeout(httpCall, 3000)
  }

  @Test
  fun `headers take precedence over annotation - min timeout wins`() {
    val action = AnnotationTimeoutAction::call.asAction(DispatchMechanism.GET) // 5000ms
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders =
          headersOf(
            "x-request-deadline",
            "8", // 8000ms
            "x-envoy-expected-rq-timeout-ms",
            "4000", // 4000ms - lowest
          )
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use the lowest timeout: 4 seconds = 4000ms
    assertDeadlineHeaderTimeout(httpCall, 4000)
  }

  @Test
  fun `annotation timeout is used when no valid headers present`() {
    val action = AnnotationTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders =
          headersOf(
            "X-Request-Deadline",
            "invalid", // Invalid value
            "x-envoy-expected-rq-timeout-ms",
            "not-a-number", // Invalid value
          )
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should fall back to annotation timeout: 5000ms
    assertDeadlineHeaderTimeout(httpCall, 5000)
  }

  @Test
  fun `mixed valid and invalid header values - only valid value wins`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders =
          headersOf(
            "X-Request-Deadline",
            "invalid", // Invalid
            "x-envoy-expected-rq-timeout-ms",
            "9000", // Valid
          )
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use the only valid timeout: 9000ms
    assertDeadlineHeaderTimeout(httpCall, 9000)
  }

  @Test
  fun `multiple valid timeout values - minimum timeout wins`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders =
          headersOf(
            "X-Request-Deadline",
            "12", // 12000ms
            "x-envoy-expected-rq-timeout-ms",
            "7000", // 7000ms - minimum
          )
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use the minimum timeout: 7000ms
    assertDeadlineHeaderTimeout(httpCall, 7000)
  }

  @Test
  fun `zero timeout values are ignored`() {
    val action = AnnotationTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders = headersOf("X-Request-Deadline", "0", "x-envoy-expected-rq-timeout-ms", "0")
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should fall back to annotation timeout since all header timeouts are zero
    assertDeadlineHeaderTimeout(httpCall, 5000)
  }

  @Test
  fun `negative timeout values are ignored`() {
    val action = AnnotationTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall(requestHeaders = headersOf("X-Request-Deadline", "-5"))
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should fall back to annotation timeout since header timeouts are negative
    assertDeadlineHeaderTimeout(httpCall, 5000)
  }

  @Test
  fun `X-Request-Deadline header supports ISO8601 duration format`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders = headersOf("X-Request-Deadline", "PT30S") // 30 seconds in ISO8601
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use ISO8601 parsed timeout: 30 seconds = 30000ms
    assertDeadlineHeaderTimeout(httpCall, 30000)
  }

  @Test
  fun `X-Request-Deadline header supports various ISO8601 duration formats`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders = headersOf("X-Request-Deadline", "PT1M30S") // 1 minute 30 seconds = 90 seconds
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use ISO8601 parsed timeout: 90 seconds = 90000ms
    assertDeadlineHeaderTimeout(httpCall, 90000)
  }

  @Test
  fun `X-Request-Deadline header falls back to seconds format when ISO8601 fails`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders = headersOf("X-Request-Deadline", "25") // Plain seconds format
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use seconds format: 25 seconds = 25000ms
    assertDeadlineHeaderTimeout(httpCall, 25000)
  }

  @Test
  fun `X-Request-Deadline header ignores invalid format and falls back to default`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall(requestHeaders = headersOf("X-Request-Deadline", "invalid-format"))
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should fall back to default timeout: 10 seconds = 10000ms
    assertDeadlineHeaderTimeout(httpCall, 10000)
  }

  @Test
  fun `gRPC dispatch uses grpc-timeout header`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GRPC)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders = headersOf("grpc-timeout", "3000m"), // 3 seconds in milliseconds
        dispatchMechanism = DispatchMechanism.GRPC,
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Verify that the deadline header was added with a deadline approximately 3 seconds from now
    assertDeadlineHeaderTimeout(httpCall, 3000)
  }

  @Test
  fun `gRPC dispatch ignores HTTP timeout headers and uses grpc-timeout`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GRPC)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall =
      createTestServletHttpCall(
        requestHeaders =
          headersOf(
            "X-Request-Deadline",
            "10", // This should be ignored for gRPC
            "grpc-timeout",
            "2000m", // 2 seconds - this should be used
          ),
        dispatchMechanism = DispatchMechanism.GRPC,
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use grpc-timeout, not HTTP timeout headers
    assertDeadlineHeaderTimeout(httpCall, 2000)
  }

  @Test
  fun `gRPC dispatch falls back to default when no grpc-timeout header`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GRPC)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall(dispatchMechanism = DispatchMechanism.GRPC)
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use default 10-second timeout
    assertDeadlineHeaderTimeout(httpCall, 10000)
  }

  @Test
  fun `gRPC dispatch uses annotation timeout when no grpc-timeout header`() {
    val action = AnnotationTimeoutAction::call.asAction(DispatchMechanism.GRPC)
    val interceptor = requestDeadlineInterceptorFactory.create(action)
    val httpCall = createTestServletHttpCall(dispatchMechanism = DispatchMechanism.GRPC)
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should use annotation timeout of 5 seconds
    assertDeadlineHeaderTimeout(httpCall, 5000)
  }

  @Test
  fun `HTTP request returns 504 when deadline already exceeded`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GET)

    // Create interceptor with enforcement mode
    val interceptorFactoryWithEnforceInbound =
      RequestDeadlineInterceptor.Factory(
        clock = clock,
        webConfig =
          WebConfig(port = 0, request_deadlines = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_INBOUND)),
        metrics = requestDeadlineMetrics,
      )
    val interceptor = interceptorFactoryWithEnforceInbound.create(action)

    // Create HttpCall with timestamp from 5 seconds ago, timeout of 1 second
    // So deadline = (now - 5000ms) + 1000ms = now - 4000ms (already passed)
    val pastTimestamp = clock.millis() - 5000
    val httpCall =
      createTestServletHttpCall(
        requestHeaders = headersOf("X-Request-Deadline", "1"), // 1 second timeout
        requestReceivedTimestamp = pastTimestamp,
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should return 504 Gateway Timeout and not proceed to TerminalInterceptor
    assertThat(httpCall.statusCode).isEqualTo(504)
  }

  @Test
  fun `gRPC request returns DEADLINE_EXCEEDED when deadline already exceeded`() {
    val action = NoTimeoutAction::call.asAction(DispatchMechanism.GRPC)

    // Create interceptor with enforcement mode
    val interceptorFactoryWithEnforceAll =
      RequestDeadlineInterceptor.Factory(
        clock = clock,
        webConfig =
          WebConfig(port = 0, request_deadlines = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_ALL)),
        metrics = requestDeadlineMetrics,
      )
    val interceptor = interceptorFactoryWithEnforceAll.create(action)

    // Create HttpCall with timestamp from 3 seconds ago, gRPC timeout of 500ms
    val pastTimestamp = clock.millis() - 3000
    val (httpCall, fakeUpstreamResponse) =
      createTestServletHttpCallWithTrailers(
        requestHeaders = headersOf("grpc-timeout", "500m"), // 500ms timeout
        requestReceivedTimestamp = pastTimestamp,
        dispatchMechanism = DispatchMechanism.GRPC,
      )
    val chain = createChain(action, httpCall, listOf(interceptor, TerminalInterceptor()))

    chain.proceed(chain.httpCall)

    // Should set gRPC DEADLINE_EXCEEDED status (4) and not proceed to TerminalInterceptor
    assertThat(fakeUpstreamResponse.getTrailer("grpc-status")).isEqualTo("4")
    assertThat(fakeUpstreamResponse.getTrailer("grpc-message")).isEqualTo("deadline exceeded: queued for too long")
  }

  private fun createTestServletHttpCall(
    url: HttpUrl = "http://test.com".toHttpUrl(),
    requestHeaders: Headers = headersOf(),
    requestReceivedTimestamp: Long = clock.millis(),
    dispatchMechanism: DispatchMechanism = DispatchMechanism.GET,
  ): ServletHttpCall {
    return ServletHttpCall(
      url = url,
      linkLayerLocalAddress = null,
      dispatchMechanism = dispatchMechanism,
      requestHeaders = requestHeaders,
      requestBody = null,
      upstreamResponse = FakeUpstreamResponse(),
      responseBody = null,
      webSocket = null,
      cookies = listOf(),
      requestReceivedTimestamp = requestReceivedTimestamp,
    )
  }

  private fun createTestServletHttpCallWithTrailers(
    url: HttpUrl = "http://test.com".toHttpUrl(),
    requestHeaders: Headers = headersOf(),
    requestReceivedTimestamp: Long = clock.millis(),
    dispatchMechanism: DispatchMechanism = DispatchMechanism.GET,
  ): Pair<ServletHttpCall, FakeUpstreamResponse> {
    val fakeUpstreamResponse = FakeUpstreamResponse()
    val httpCall =
      ServletHttpCall(
        url = url,
        linkLayerLocalAddress = null,
        dispatchMechanism = dispatchMechanism,
        requestHeaders = requestHeaders,
        requestBody = null,
        upstreamResponse = fakeUpstreamResponse,
        responseBody = null,
        webSocket = null,
        cookies = listOf(),
        requestReceivedTimestamp = requestReceivedTimestamp,
      )
    return httpCall to fakeUpstreamResponse
  }

  private fun createChain(
    action: Action,
    httpCall: ServletHttpCall,
    interceptors: List<NetworkInterceptor>,
  ): NetworkChain {
    return RealNetworkChain(action, TestAction(), httpCall, interceptors)
  }

  private fun assertDeadlineHeaderTimeout(httpCall: ServletHttpCall, expectedTimeoutMs: Long, tolerance: Long = 10) {
    val deadlineHeader = httpCall.requestHeaders[MISK_REQUEST_DEADLINE_HEADER]
    assertThat(deadlineHeader).isNotNull()
    val deadline = Instant.parse(deadlineHeader!!)
    val now = clock.instant()
    val remaining = Duration.between(now, deadline)
    assertThat(remaining).isGreaterThan(Duration.ofMillis(expectedTimeoutMs - tolerance))
    assertThat(remaining).isLessThanOrEqualTo(Duration.ofMillis(expectedTimeoutMs))
  }

  internal class NoTimeoutAction @Inject constructor() : WebAction {
    @Get("/no-timeout") fun call(): String = "success"
  }

  internal class AnnotationTimeoutAction @Inject constructor() : WebAction {
    @Get("/annotation-timeout") @RequestDeadlineTimeout(timeoutMs = 5000) fun call(): String = "success"
  }

  internal class TestAction @Inject constructor() : WebAction

  internal class TerminalInterceptor : NetworkInterceptor {
    override fun intercept(chain: NetworkChain) {
      chain.httpCall.statusCode = 200
    }
  }

  // Simple fake UpstreamResponse for testing
  internal class FakeUpstreamResponse(override var statusCode: Int = 200, override val headers: Headers = headersOf()) :
    ServletHttpCall.UpstreamResponse {
    private val headersBuilder = Headers.Builder()
    private val trailersMap = mutableMapOf<String, String>()

    override fun setHeader(name: String, value: String) {
      headersBuilder.set(name, value)
    }

    override fun addHeaders(headers: Headers) {
      headersBuilder.addAll(headers)
    }

    override fun requireTrailers() {
      // no-op for testing
    }

    override fun setTrailer(name: String, value: String) {
      trailersMap[name] = value
    }

    fun getTrailer(name: String): String? = trailersMap[name]

    override fun initWebSocketListener(webSocketListener: WebSocketListener) {
      // no-op for testing
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      bind<WebConfig>().toInstance(WebConfig(port = 0))
      bind<RequestDeadlineInterceptor.Factory>()
    }
  }
}
