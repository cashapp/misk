package misk.web.interceptors

import com.netflix.concurrency.limits.limit.SettableLimit
import com.netflix.concurrency.limits.limiter.SimpleLimiter
import misk.Action
import misk.asAction
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.time.FakeClockModule
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.inject.Inject

@MiskTest
class ConcurrencyLimitsInterceptorTest {
  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var factory: ConcurrencyLimitsInterceptor.Factory
  @Inject private lateinit var clock: FakeClock

  @Test
  fun happyPath() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val interceptor = factory.create(action)!!
    assertThat(call(action, interceptor, callDuration = Duration.ofMillis(100), statusCode = 200))
        .isEqualTo(CallResult(callWasShed = false, statusCode = 200))
  }

  @Test
  fun limitReached() {
    val action = HelloAction::call.asAction(DispatchMechanism.GET)
    val limitZero = SimpleLimiter.Builder()
        .limit(SettableLimit(0))
        .build<String>()
    val interceptor = ConcurrencyLimitsInterceptor(action.name, limitZero)
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

  private fun call(
    action: Action,
    interceptor: NetworkInterceptor,
    callDuration: Duration = Duration.ofMillis(100),
    statusCode: Int = 200
  ): CallResult {
    val terminalInterceptor = TerminalInterceptor(callDuration, statusCode)
    val httpCall = FakeHttpCall(url = "https://example.com/hello".toHttpUrl())
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
      install(FakeClockModule())
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
}
