package misk.web.interceptors

import ch.qos.logback.classic.Level
import com.netflix.concurrency.limits.Limiter
import com.netflix.concurrency.limits.limit.AbstractLimit
import com.netflix.concurrency.limits.limit.SettableLimit
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import misk.Action
import misk.MiskTestingServiceModule
import misk.asAction
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.web.AvailableWhenDegraded
import misk.web.DispatchMechanism
import misk.web.FakeHttpCall
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealNetworkChain
import misk.web.actions.LivenessCheckAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.StatusAction
import misk.web.actions.WebAction
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector

@MiskTest(startService = true)
class ConcurrencyLimitsInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var factory: ConcurrencyLimitsInterceptor.Factory
  @Inject private lateinit var clock: FakeClock
  @Inject lateinit var logCollector: LogCollector
  @Inject lateinit var countingLimiterFactory: CountingLimiterFactory

  @Test
  fun happyPath() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val interceptor = factory.create(action)!!
    assertThat(call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200))
      .isEqualTo(CallResult(callWasShed = false, statusCode = 200))
    assertThat(logCollector.takeMessages()).isEmpty()
  }

  @Test
  fun limitReached() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val limitZero = SimpleLimiter.Builder()
      .limit(SettableLimit(0))
      .build<String>()
    val interceptor = ConcurrencyLimitsInterceptor(factory, action, limitZero, clock)
    assertThat(call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200))
      .isEqualTo(CallResult(callWasShed = true, statusCode = 503))
  }

  @Test
  fun noLimitsOnHealthCriticalEndpoints() {
    val livenessCheck = LivenessCheckAction::livenessCheck.asAction(DispatchMechanism.GET)
    assertThat(factory.create(livenessCheck)).isNull()

    val readinessCheck = ReadinessCheckAction::readinessCheck.asAction(DispatchMechanism.GET)
    assertThat(factory.create(readinessCheck)).isNull()

    val getStatus = StatusAction::getStatus.asAction(DispatchMechanism.GET)
    assertThat(factory.create(getStatus)).isNull()
  }

  @Test
  fun limitsOnForUnannotatedEndpoints() {
    val optInAction = UnannotatedAction::call.asAction(DispatchMechanism.GET)
    assertThat(factory.create(optInAction)).isNotNull()
  }

  @Test
  fun noLimitsOnOptOutEndpoints() {
    val optOutAction = OptOutAction::call.asAction(DispatchMechanism.GET)
    assertThat(factory.create(optOutAction)).isNull()
  }

  @Test
  fun atMostOneErrorLoggedPerMinute() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val limitZero = SimpleLimiter.Builder()
      .limit(SettableLimit(0))
      .build<String>()
    val interceptor = ConcurrencyLimitsInterceptor(factory, action, limitZero, clock)
    // First call logs an error.
    call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200)
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR))
      .containsExactly("concurrency limits interceptor shedding HelloAction; Quota-Path=null")

    // Subsequent calls don't.
    call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200)
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR)).isEmpty()

    // One minute later we get errors again.
    clock.setNow(clock.instant().plus(1, ChronoUnit.MINUTES))
    call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200)
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR))
      .containsExactly("concurrency limits interceptor shedding HelloAction; Quota-Path=null")
  }

  @Test
  fun usesProvidedLimiter() {
    val action = CustomLimiterAction::call.asAction(DispatchMechanism.GET)
    val interceptor = factory.create(action)!!
    // The bound limiter has a 0 limit.
    assertThat(call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200))
      .isEqualTo(CallResult(callWasShed = true, statusCode = 503))
  }

  @Test
  fun quotaPathUsesIndependentLimiters() {
    val action = CountingLimiterAction::call.asAction(DispatchMechanism.GET)
    val interceptor = factory.create(action)!!

    call(action, interceptor)
    assertThat(countingLimiterFactory.countingLimits.map { it.count })
      .containsExactly(1) // Exactly one call on the only limiter.

    call(action, interceptor)
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    assertThat(countingLimiterFactory.countingLimits.map { it.count })
      .containsExactly(2, 3) // 2 calls on limiter[0], 3 calls on limiter[1]

    call(action, interceptor, quotaPath = "/event_consumer/topic_bar")
    call(action, interceptor, quotaPath = "/event_consumer/topic_baz")
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    assertThat(countingLimiterFactory.countingLimits.map { it.count })
      .containsExactly(2, 4, 1, 1)
  }

  private fun call(
    action: Action,
    interceptor: NetworkInterceptor,
    callDuration: Duration = Duration.ofMillis(100),
    statusCode: Int = 200,
    quotaPath: String? = null
  ): CallResult {
    val terminalInterceptor = TerminalInterceptor(callDuration, statusCode)
    val requestHeaders = Headers.Builder()
    if (quotaPath != null) {
      requestHeaders.add("Quota-Path", quotaPath)
    }
    val httpCall = FakeHttpCall(
      url = "https://example.com/hello".toHttpUrl(),
      requestHeaders = requestHeaders.build(),
    )
    val chain = RealNetworkChain(
      action,
      HelloAction(),
      httpCall,
      listOf(interceptor, terminalInterceptor)
    )
    chain.proceed(chain.httpCall)
    return CallResult(
      callWasShed = terminalInterceptor.callWasShed,
      statusCode = httpCall.statusCode
    )
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(MiskTestingServiceModule())

      multibind<ConcurrencyLimiterFactory>().to<CustomLimiterFactory>()
      multibind<ConcurrencyLimiterFactory>().to<CountingLimiterFactory>()
    }
  }

  @Singleton
  class CustomLimiterFactory @Inject constructor() : ConcurrencyLimiterFactory {
    override fun create(action: Action): Limiter<String>? {
      if (action.function == CustomLimiterAction::call) {
        return SimpleLimiter.Builder()
          .limit(SettableLimit(0))
          .build()
      }
      return null
    }
  }

  /** A limiter factory that returns CountingLimit instances. */
  @Singleton
  class CountingLimiterFactory @Inject constructor() : ConcurrencyLimiterFactory {
    val countingLimits = mutableListOf<CountingLimit>()

    override fun create(action: Action): Limiter<String>? {
      if (action.function == CountingLimiterAction::call) {
        val countingLimit = CountingLimit()
        countingLimits += countingLimit
        return SimpleLimiter.Builder()
          .limit(countingLimit)
          .build()
      }
      return null
    }
  }

  /** A limit that counts how many times it is used. */
  class CountingLimit : AbstractLimit(1) {
    var count = 0
    override fun _update(startTime: Long, rtt: Long, inflight: Int, didDrop: Boolean): Int {
      count++
      return limit
    }
  }

  /** Simulate forwarding through actions that return an expected result. */
  inner class TerminalInterceptor(
    private val callDuration: Duration,
    private val statusCode: Int
  ) : NetworkInterceptor {
    var callWasShed = true

    override fun intercept(chain: NetworkChain) {
      callWasShed = false
      clock.add(callDuration)
      chain.httpCall.statusCode = statusCode
    }
  }

  data class CallResult(
    val callWasShed: Boolean,
    val statusCode: Int
  )
}

internal class HelloAction : WebAction {
  @Get("/hello")
  fun call(): String = "hello"
}

internal class UnannotatedAction : WebAction {
  @Get("/chill")
  fun call(): String = "chill"
}

internal class OptOutAction : WebAction {
  @Get("/important")
  @AvailableWhenDegraded
  fun call(): String = "important"
}

internal class CustomLimiterAction : WebAction {
  @Get("/custom-limiter")
  fun call(): String = "custom-limiter"
}

internal class CountingLimiterAction : WebAction {
  @Get("/counting-limiter")
  fun call(): String = "counting-limiter"
}
