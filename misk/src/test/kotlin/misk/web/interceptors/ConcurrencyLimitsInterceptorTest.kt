package misk.web.interceptors

import ch.qos.logback.classic.Level
import com.google.inject.util.Modules
import com.netflix.concurrency.limits.Limiter
import com.netflix.concurrency.limits.limit.SettableLimit
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import io.prometheus.client.CollectorRegistry
import misk.Action
import misk.MiskTestingServiceModule
import misk.asAction
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.AvailableWhenDegraded
import misk.web.DispatchMechanism
import misk.web.FakeHttpCall
import misk.web.Get
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.RealNetworkChain
import misk.web.WebConfig
import misk.web.actions.LivenessCheckAction
import misk.web.actions.ReadinessCheckAction
import misk.web.actions.StatusAction
import misk.web.actions.WebAction
import misk.web.concurrencylimits.ConcurrencyLimiterFactory
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import misk.logging.LogCollector
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import jakarta.inject.Inject
import jakarta.inject.Singleton

@MiskTest(startService = true)
class ConcurrencyLimitsInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var factory: ConcurrencyLimitsInterceptor.Factory
  @Inject private lateinit var clock: FakeNanoClock
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var prometheusRegistry: CollectorRegistry
  @Inject private lateinit var  enabledFeature: TestableMiskConcurrencyLimiterFeature

  @Test
  fun happyPath() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val interceptor = factory.create(action)!!
    assertThat(call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200))
      .isEqualTo(CallResult(callWasShed = false, statusCode = 200))
    assertThat(logCollector.takeMessages()[0]).isEqualTo("Starting ready service")
    assertThat(callSuccessCount("HelloAction")).isEqualTo(1.0)
  }

  @Test
  fun limitReached() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val limitZero = SimpleLimiter.Builder()
      .limit(SettableLimit(0))
      .build<String>()
    val interceptor =
      ConcurrencyLimitsInterceptor(
        factory = factory,
        action = action,
        defaultLimiter = limitZero,
        clock = clock,
        logLevel = org.slf4j.event.Level.ERROR,
        enabledFeature = enabledFeature
      )
    assertThat(call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200))
      .isEqualTo(CallResult(callWasShed = true, statusCode = 503))
  }

  @Test
  fun limitReachedDisabled() {
    enabledFeature.isEnabled = false

    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val limitZero = SimpleLimiter.Builder()
      .limit(SettableLimit(0))
      .build<String>()
    val interceptor =
      ConcurrencyLimitsInterceptor(
        factory = factory,
        action = action,
        defaultLimiter = limitZero,
        clock = clock,
        logLevel = org.slf4j.event.Level.ERROR,
        enabledFeature = enabledFeature
      )
    assertThat(call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200))
      .isEqualTo(CallResult(callWasShed = false, statusCode = 200))
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
    val interceptor =
      ConcurrencyLimitsInterceptor(
        factory = factory,
        action = action,
        defaultLimiter = limitZero,
        clock = clock,
        logLevel = org.slf4j.event.Level.ERROR,
        enabledFeature = enabledFeature
      )
    // First call logs an error.
    call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200)
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR))
      .containsExactly(
        "concurrency limits interceptor shedding HelloAction; " +
          "Quota-Path=null; inflight=0; limit=0"
      )

    // Subsequent calls don't.
    call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200)
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR)).isEmpty()

    // One minute later we get errors again.
    clock.setNow(clock.instant().plus(1, ChronoUnit.MINUTES))
    call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200)
    assertThat(logCollector.takeMessages(minLevel = Level.ERROR))
      .containsExactly(
        "concurrency limits interceptor shedding HelloAction; " +
          "Quota-Path=null; inflight=0; limit=0"
      )
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
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val interceptor = factory.create(action)!!

    call(action, interceptor)
    assertThat(callSuccessCount("HelloAction"))
      .isEqualTo(1.0)  // Exactly one call on the only limiter.

    call(action, interceptor)
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    assertThat(callSuccessCount("HelloAction")).isEqualTo(2.0)
    assertThat(callSuccessCount("/event_consumer/topic_foo")).isEqualTo(3.0)

    call(action, interceptor, quotaPath = "/event_consumer/topic_bar")
    call(action, interceptor, quotaPath = "/event_consumer/topic_baz")
    call(action, interceptor, quotaPath = "/event_consumer/topic_foo")
    assertThat(callSuccessCount("HelloAction")).isEqualTo(2.0)
    assertThat(callSuccessCount("/event_consumer/topic_foo")).isEqualTo(4.0)
    assertThat(callSuccessCount("/event_consumer/topic_bar")).isEqualTo(1.0)
    assertThat(callSuccessCount("/event_consumer/topic_baz")).isEqualTo(1.0)
  }

  @Test
  fun limiterResolutionExceedsMicroseconds() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val limiter = factory.createLimiterForAction(action, null)
    val interceptor =
      ConcurrencyLimitsInterceptor(
        factory = factory,
        action = action,
        defaultLimiter = limiter,
        clock = clock,
        logLevel = org.slf4j.event.Level.ERROR,
        enabledFeature = enabledFeature
      )

    // load up some inflight requests so we get past "Prevent upward drift if not close to the limit"
    repeat(10) {
      limiter.acquire(action.name)
    }
    // establish a baseline rtt
    call(action, interceptor, callDuration = Duration.ofNanos(2400 * 1000))
    // new request just 0.8ms longer. This should bump the limit up because its rtt implies a
    // fairly empty queue.
    call(action, interceptor, callDuration = Duration.ofNanos(3200 * 1000))
    // metrics updated before, not after, request, so we need another request to trigger an update
    call(action, interceptor, callDuration = Duration.ofNanos(2500 * 1000))

    val limit = prometheusRegistry.getSampleValue(
      "concurrency_limits_limit",
      arrayOf("quota_path"),
      arrayOf("HelloAction")
    )
    // VegasLimit should've bumped _up_ the concurrency limit
    assertThat(limit).isGreaterThanOrEqualTo(20.0)
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

  private fun callSuccessCount(id: String): Double {
    return prometheusRegistry.getSampleValue(
      "concurrency_limits_outcomes_total",
      arrayOf("quota_path", "outcome"),
      arrayOf(id, "success")
    )
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      bind<MiskConcurrencyLimiterFeature>().to(TestableMiskConcurrencyLimiterFeature::class.java)

      install(LogCollectorModule())
      install(Modules.override(MiskTestingServiceModule()).with(object : KAbstractModule() {
        override fun configure() {
          bind<Clock>().to<FakeNanoClock>()
          bind<FakeNanoClock>().toInstance(FakeNanoClock())
        }
      }))

      bind<WebConfig>().toInstance(
        WebConfig(
          port = 0,
        )
      )

      multibind<ConcurrencyLimiterFactory>().to<CustomLimiterFactory>()
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

@Singleton
class TestableMiskConcurrencyLimiterFeature @Inject constructor() :MiskConcurrencyLimiterFeature{
  var isEnabled = true
  override fun enabled() = isEnabled
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
