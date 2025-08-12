package misk.client

import misk.MiskTestingServiceModule
import misk.exceptions.GatewayTimeoutException
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.RequestDeadlineMode
import misk.web.RequestDeadlinesConfig
import misk.web.requestdeadlines.RequestDeadline
import misk.web.requestdeadlines.RequestDeadlineMetrics
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import kotlin.reflect.full.createType
import jakarta.inject.Inject

@MiskTest(startService = false)
class DeadlinePropagationInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var metrics: RequestDeadlineMetrics
  private val fixedClock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), java.time.ZoneOffset.UTC)

  @Test
  fun `no deadline in scope - uses fallback timeout`() {
    val requestDeadlineActionScope = TestActionScope(null)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.PROPAGATE_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(readTimeoutMillis = 5000)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with fallback timeout of 5000ms
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("5000")
  }

  @Test
  fun `deadline expired + enforce outbound - throws exception`() {
    val expiredDeadline = RequestDeadline(fixedClock, fixedClock.instant().minusMillis(100))
    val requestDeadlineActionScope = TestActionScope(expiredDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_OUTBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val exception = assertThrows<GatewayTimeoutException> {
      interceptor.intercept(chain)
    }

    assertThat(exception.message).contains("Deadline already expired")
  }

  @Test
  fun `deadline expired + enforce all - throws exception`() {
    val expiredDeadline = RequestDeadline(fixedClock, fixedClock.instant().minusMillis(200))
    val requestDeadlineActionScope = TestActionScope(expiredDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_ALL)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val exception = assertThrows<GatewayTimeoutException> {
      interceptor.intercept(chain)
    }

    assertThat(exception.message).contains("Deadline already expired")
  }

  @Test
  fun `deadline expired + propagate only - continues without headers`() {
    val expiredDeadline = RequestDeadline(fixedClock, fixedClock.instant().minusMillis(150))
    val requestDeadlineActionScope = TestActionScope(expiredDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.PROPAGATE_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should proceed with original request (no deadline headers added)
    assertThat(chain.proceededWithOriginalRequest).isTrue()
  }

  @Test
  fun `deadline expired + enforce inbound - continues without headers`() {
    val expiredDeadline = RequestDeadline(fixedClock, fixedClock.instant().minusMillis(75))
    val requestDeadlineActionScope = TestActionScope(expiredDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_INBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should proceed with original request (no deadline headers added)
    assertThat(chain.proceededWithOriginalRequest).isTrue()
  }

  @Test
  fun `deadline not expired + propagate only - sets deadline headers`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(5))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.PROPAGATE_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with remaining deadline
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("5000")
  }

  @Test
  fun `deadline not expired + enforce all - sets deadline headers`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(3))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_ALL)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with remaining deadline
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("3000")
  }

  @Test
  fun `deadline not expired + metrics only mode - no deadline headers`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(2))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.METRICS_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should proceed with original request (no deadline headers added)
    assertThat(chain.proceededWithOriginalRequest).isTrue()
  }

  @Test
  fun `grpc request - sets grpc-timeout header`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(4))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.PROPAGATE_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(grpcRequest = true)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // gRPC request should only set grpc-timeout header, not HTTP deadline header
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isNull()
    assertThat(chain.builtRequest?.header("grpc-timeout")).isNotNull()
  }

  @Test
  fun `http request - sets http deadline header`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(4))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.PROPAGATE_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(grpcRequest = false)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // HTTP request should only set HTTP deadline header, not grpc-timeout header
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("4000")
    assertThat(chain.builtRequest?.header("grpc-timeout")).isNull()
  }

  @Test
  fun `no deadline in scope + metrics only mode - proceeds with original request`() {
    val requestDeadlineActionScope = TestActionScope(null)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.METRICS_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should proceed with original request, no headers or metrics
    assertThat(chain.proceededWithOriginalRequest).isTrue()
  }

  @Test
  fun `no deadline in scope + enforce outbound mode - uses fallback timeout`() {
    val requestDeadlineActionScope = TestActionScope(null)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_OUTBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(readTimeoutMillis = 3000)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with fallback timeout of 3000ms
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("3000")
  }

  @Test
  fun `no deadline in scope + enforce inbound mode - uses fallback timeout`() {
    val requestDeadlineActionScope = TestActionScope(null)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_INBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(readTimeoutMillis = 7000)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with fallback timeout of 7000ms
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("7000")
  }

  @Test
  fun `no deadline in scope + enforce all mode - uses fallback timeout`() {
    val requestDeadlineActionScope = TestActionScope(null)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_ALL)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(readTimeoutMillis = 2500)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with fallback timeout of 2500ms
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("2500")
  }

  @Test
  fun `deadline expired + metrics only mode - emits metrics but no enforcement`() {
    val expiredDeadline = RequestDeadline(fixedClock, fixedClock.instant().minusMillis(300))
    val requestDeadlineActionScope = TestActionScope(expiredDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.METRICS_ONLY)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should proceed with original request (no deadline headers, no enforcement)
    assertThat(chain.proceededWithOriginalRequest).isTrue()
  }

  @Test
  fun `deadline not expired + enforce outbound - sets deadline headers`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(6))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_OUTBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with remaining deadline
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("6000")
  }

  @Test
  fun `deadline not expired + enforce inbound - sets deadline headers`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(8))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_INBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with remaining deadline
    assertThat(chain.builtRequest?.header("x-envoy-expected-rq-timeout-ms")).isEqualTo("8000")
  }

  // Test implementations
  private class TestActionScope(private val deadline: RequestDeadline?) : ActionScoped<RequestDeadline> {
    override fun get(): RequestDeadline = deadline ?: throw IllegalStateException("No deadline in scope")
    override fun getIfInScope(): RequestDeadline? = deadline
  }

  private fun createTestClientAction(): ClientAction {
    // Create a simple function reference to use as the ClientAction function parameter
    val dummyFunction = ::toString
    return ClientAction(
      name = "TestClient.testMethod",
      function = dummyFunction,
      parameterTypes = emptyList(),
      returnType = String::class.createType()
    )
  }

  private class TestInterceptorChain(
    private val readTimeoutMillis: Int = 10000,
    grpcRequest: Boolean = false,
  ) : Interceptor.Chain {
    private val originalRequest = if (grpcRequest) {
      Request.Builder()
        .url("http://test.example.com")
        .header("te", "trailers")
        .build()
    } else {
      Request.Builder().url("http://test.example.com").build()
    }
    var builtRequest: Request? = null
    var proceededWithOriginalRequest = false

    override fun readTimeoutMillis(): Int = readTimeoutMillis
    override fun request(): Request = originalRequest

    override fun proceed(request: Request): Response {
      if (request == originalRequest) {
        proceededWithOriginalRequest = true
      } else {
        builtRequest = request
      }
      return Response.Builder()
        .request(request)
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()
    }

    override fun call(): okhttp3.Call = throw NotImplementedError("Not needed for test")
    override fun connectTimeoutMillis(): Int = throw NotImplementedError("Not needed for test")
    override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = throw NotImplementedError("Not needed for test")
    override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = throw NotImplementedError("Not needed for test")
    override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = throw NotImplementedError("Not needed for test")
    override fun writeTimeoutMillis(): Int = throw NotImplementedError("Not needed for test")
    override fun connection(): okhttp3.Connection? = null
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
    }
  }
}
