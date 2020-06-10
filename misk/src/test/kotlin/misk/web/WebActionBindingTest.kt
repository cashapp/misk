package misk.web

import misk.Action
import misk.asAction
import misk.web.actions.WebAction
import okio.BufferedSink
import okio.BufferedSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class WebActionBindingTest {
  private val defaultFactory = FakeFeatureBindingFactory()
  private val requestBodyFactory = FakeFeatureBindingFactory(claimRequestBody = true)
  private val responseBodyFactory = FakeFeatureBindingFactory(claimResponseBody = true)
  private val returnValueFactory = FakeFeatureBindingFactory(claimReturnValue = true)
  private val parametersFactory = FakeFeatureBindingFactory(
      claimParameterValues = mutableMapOf(0 to "zero", 1 to "one"))
  private val webActionBindingFactory = WebActionBinding.Factory(listOf(
      defaultFactory,
      requestBodyFactory,
      responseBodyFactory,
      returnValueFactory,
      parametersFactory
  ))
  private val pathPattern = PathPattern.parse("/")
  private val voidApiCallAction = TestAction::voidApiCall.asAction(DispatchMechanism.POST)

  @Test
  internal fun happyPath() {
    val binding = webActionBindingFactory.create(
        TestAction::fakeApiCall.asAction(DispatchMechanism.POST), pathPattern)
    val httpCall = FakeHttpCall()
    val matcher = pathPattern.matcher(httpCall.url)!!

    // Request and response bodies are consumed and parameters are provided.
    val parameters = binding.beforeCall(TestAction(), httpCall, matcher)
    assertThat(parameters).containsExactly("zero", "one")
    assertThat(httpCall.takeRequestBody()).isNull()
    assertThat(requestBodyFactory.result?.requestBody).isNotNull()
    assertThat(httpCall.takeResponseBody()).isNull()
    assertThat(responseBodyFactory.result?.responseBody).isNotNull()

    // Return value is consumed.
    binding.afterCall(TestAction(), httpCall, matcher, "hello")
    assertThat(returnValueFactory.result?.returnValue).isEqualTo("hello")
  }

  @Test
  internal fun unclaimedParameter() {
    parametersFactory.claimParameterValues.remove(1)

    val e = assertFailsWith<IllegalStateException> {
      webActionBindingFactory.create(TestAction::fakeApiCall.asAction(DispatchMechanism.POST),
          pathPattern)
    }
    assertThat(e).hasMessage("${TestAction::fakeApiCall.asAction(
        DispatchMechanism.POST)} parameter 1 not claimed (did you forget @RequestBody ?)")
  }

  @Test
  internal fun claimButReturnNull() {
    requestBodyFactory.result = null

    val e = assertFailsWith<IllegalStateException> {
      webActionBindingFactory.create(TestAction::fakeApiCall.asAction(DispatchMechanism.POST),
          pathPattern)
    }
    assertThat(e).hasMessage("FakeFactory returned null after making a claim")
  }

  @Test
  internal fun claimParameterAndReturnValue() {
    returnValueFactory.claimReturnValue = false
    parametersFactory.claimReturnValue = true
    parametersFactory.result!!.claimReturnValue = true

    val binding = webActionBindingFactory.create(
        TestAction::fakeApiCall.asAction(DispatchMechanism.POST), pathPattern)
    val httpCall = FakeHttpCall()
    val matcher = pathPattern.matcher(httpCall.url)!!

    // Request and response bodies are consumed and parameters are provided.
    val parameters = binding.beforeCall(TestAction(), httpCall, matcher)
    assertThat(parameters).containsExactly("zero", "one")
    assertThat(httpCall.takeRequestBody()).isNull()
    assertThat(requestBodyFactory.result?.requestBody).isNotNull()
    assertThat(httpCall.takeResponseBody()).isNull()
    assertThat(responseBodyFactory.result?.responseBody).isNotNull()

    // Return value is consumed.
    binding.afterCall(TestAction(), httpCall, matcher, "hello")
    assertThat(parametersFactory.result?.returnValue).isEqualTo("hello")
  }

  @Test
  internal fun doubleClaimParameter() {
    defaultFactory.claimParameterValues[1] = "coke"
    parametersFactory.claimParameterValues[1] = "pepsi"

    val e = assertFailsWith<IllegalStateException> {
      webActionBindingFactory.create(TestAction::fakeApiCall.asAction(DispatchMechanism.POST),
          pathPattern)
    }
    assertThat(e).hasMessage("already claimed by ${defaultFactory.result}")
  }

  @Test
  internal fun claimButDoNotSupplyParameter() {
    val binding = webActionBindingFactory.create(
        TestAction::fakeApiCall.asAction(DispatchMechanism.POST), pathPattern)
    val httpCall = FakeHttpCall()
    val matcher = pathPattern.matcher(httpCall.url)!!

    parametersFactory.result!!.claimParameterValues.remove(1)
    val parameters = binding.beforeCall(TestAction(), httpCall, matcher)
    assertThat(parameters).containsExactly("zero", null)
  }

  @Test
  internal fun claimGetRequestBody() {
    val e = assertFailsWith<IllegalStateException> {
      webActionBindingFactory.create(
          TestAction::fakeApiCall.asAction(DispatchMechanism.GET), pathPattern)
    }
    assertThat(e).hasMessage("cannot claim request body of GET")
  }

  @Test
  internal fun claimDeleteRequestBody() {
    val e = assertFailsWith<IllegalStateException> {
      webActionBindingFactory.create(
          TestAction::fakeApiCall.asAction(DispatchMechanism.DELETE), pathPattern)
    }
    assertThat(e).hasMessage("cannot claim request body of DELETE")
  }

  @Test
  internal fun claimReturnValueOnActionThatReturnsUnit() {
    val e = assertFailsWith<IllegalStateException> {
      webActionBindingFactory.create(voidApiCallAction, pathPattern)
    }
    assertThat(e).hasMessage("cannot claim the return value of $voidApiCallAction which has none")
  }

  @Suppress("UNUSED_PARAMETER")
  class TestAction : WebAction {
    fun fakeApiCall(p0: String, p1: String): String = TODO()
    fun voidApiCall(p0: String, p1: String): Unit = TODO()
  }

  internal class FakeFeatureBinding(
    var claimRequestBody: Boolean,
    var claimParameterValues: MutableMap<Int, Any>,
    var claimResponseBody: Boolean,
    var claimReturnValue: Boolean
  ) : FeatureBinding {
    var requestBody: BufferedSource? = null
    var responseBody: BufferedSink? = null
    var returnValue: Any? = null

    override fun beforeCall(subject: FeatureBinding.Subject) {
      if (claimRequestBody) {
        requestBody = subject.takeRequestBody()
      }
      for ((index, value) in claimParameterValues) {
        subject.setParameter(index, value)
      }
      if (claimResponseBody) {
        responseBody = subject.takeResponseBody()
      }
    }

    override fun afterCall(subject: FeatureBinding.Subject) {
      if (claimReturnValue) {
        returnValue = subject.takeReturnValue()
      }
    }

    override fun toString() = "FakeFeatureBinding"
  }

  internal class FakeFeatureBindingFactory(
    var claimRequestBody: Boolean = false,
    var claimParameterValues: MutableMap<Int, Any> = mutableMapOf(),
    var claimResponseBody: Boolean = false,
    var claimReturnValue: Boolean = false,
    var result: FakeFeatureBinding? = FakeFeatureBinding(
        claimRequestBody, claimParameterValues, claimResponseBody, claimReturnValue)
  ) : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: FeatureBinding.Claimer
    ): FeatureBinding? {
      if (claimRequestBody) claimer.claimRequestBody()
      for (index in claimParameterValues.keys) {
        claimer.claimParameter(index)
      }
      if (claimResponseBody) claimer.claimResponseBody()
      if (claimReturnValue) claimer.claimReturnValue()
      return result
    }

    override fun toString() = "FakeFactory"
  }
}