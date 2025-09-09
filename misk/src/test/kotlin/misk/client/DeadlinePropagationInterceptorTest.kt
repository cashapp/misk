package misk.client

import com.squareup.wire.GrpcMethod
import com.squareup.wire.ProtoAdapter
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.RequestDeadlineMode
import misk.web.RequestDeadlinesConfig
import misk.web.requestdeadlines.DeadlineExceededException
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

    val chain = TestInterceptorChain(callTimeoutMillis = 15000)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with fallback timeout of 15s
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT15S")
  }

  @Test
  fun `deadline expired + enforce outbound - throws exception`() {
    val expiredDeadline = RequestDeadline(fixedClock, fixedClock.instant().minusMillis(100))
    val requestDeadlineActionScope = TestActionScope(expiredDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_OUTBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain()

    val exception = assertThrows<DeadlineExceededException> {
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

    val exception = assertThrows<DeadlineExceededException> {
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
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT5S")
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
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT3S")
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
    // gRPC request in PROPAGATE_ONLY mode should set shadow header, not HTTP deadline header or enforcement header
    assertThat(chain.builtRequest?.header("x-request-deadline")).isNull()
    assertThat(chain.builtRequest?.header("grpc-timeout")).isNull()
    assertThat(chain.builtRequest?.header("x-grpc-timeout-propagate")).isNotNull()
  }

  @Test
  fun `grpc request + enforce mode - sets real grpc-timeout header`() {
    val validDeadline = RequestDeadline(fixedClock, fixedClock.instant().plusSeconds(6))
    val requestDeadlineActionScope = TestActionScope(validDeadline)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_OUTBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(grpcRequest = true)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // gRPC request in ENFORCE mode should set real grpc-timeout header, not shadow header or HTTP header
    assertThat(chain.builtRequest?.header("x-request-deadline")).isNull()
    assertThat(chain.builtRequest?.header("x-grpc-timeout-propagate")).isNull()
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
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT4S")
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

    val chain = TestInterceptorChain(callTimeoutMillis = 3750)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with fallback timeout of 3750ms
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT3.75S")
  }

  @Test
  fun `no deadline in scope + enforce inbound mode - uses fallback timeout`() {
    val requestDeadlineActionScope = TestActionScope(null)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_INBOUND)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(callTimeoutMillis = 6500)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    // Should set headers with fallback timeout of 6.5s
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT6.5S")
  }

  @Test
  fun `no deadline in scope + enforce all mode + no callTimeout - proceeds with original request`() {
    val requestDeadlineActionScope = TestActionScope(null)
    val config = RequestDeadlinesConfig(mode = RequestDeadlineMode.ENFORCE_ALL)
    val clientAction = createTestClientAction()
    val interceptor = DeadlinePropagationInterceptor(clientAction, config, requestDeadlineActionScope, metrics)

    val chain = TestInterceptorChain(callTimeoutMillis = 0)

    val response = interceptor.intercept(chain)

    assertThat(response).isNotNull()
    assertThat(chain.proceededWithOriginalRequest).isTrue()
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
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT6S")
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
    assertThat(chain.builtRequest?.header("x-request-deadline")).isEqualTo("PT8S")
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
    private val callTimeoutMillis: Int = 0,
    grpcRequest: Boolean = false,
  ) : Interceptor.Chain {
    private val originalRequest = Request.Builder()
      .url("http://test.example.com")
      .apply {
        if (grpcRequest) {
          tag(GrpcMethod::class.java, GrpcMethod(
            path = "/TestService/TestMethod",
            requestAdapter = ProtoAdapter.STRING,
            responseAdapter = ProtoAdapter.STRING
          ))
        }
      }
      .build()
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

    override fun call(): okhttp3.Call = TestCall(callTimeoutMillis)
    override fun connectTimeoutMillis(): Int = throw NotImplementedError("Not needed for test")
    override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = throw NotImplementedError("Not needed for test")
    override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = throw NotImplementedError("Not needed for test")
    override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = throw NotImplementedError("Not needed for test")
    override fun writeTimeoutMillis(): Int = throw NotImplementedError("Not needed for test")
    override fun connection(): okhttp3.Connection? = null
  }

  private class TestCall(private val callTimeoutMillis: Int) : okhttp3.Call {
    override fun request(): Request = throw NotImplementedError("Not needed for test")
    override fun execute(): Response = throw NotImplementedError("Not needed for test")
    override fun enqueue(responseCallback: okhttp3.Callback) = throw NotImplementedError("Not needed for test")
    override fun cancel() = throw NotImplementedError("Not needed for test")
    override fun isExecuted(): Boolean = throw NotImplementedError("Not needed for test")
    override fun isCanceled(): Boolean = throw NotImplementedError("Not needed for test")
    override fun clone(): okhttp3.Call = throw NotImplementedError("Not needed for test")

    override fun timeout(): okio.Timeout = TestTimeout(callTimeoutMillis)
  }

  private class TestTimeout(private val callTimeoutMillis: Int) : okio.Timeout() {
    override fun timeoutNanos(): Long = callTimeoutMillis.toLong() * 1_000_000L
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
    }
  }
}
