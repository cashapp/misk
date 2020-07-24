package misk.web

import misk.Action
import misk.web.actions.WebAction
import okio.BufferedSink
import okio.BufferedSource
import java.util.regex.Matcher
import javax.inject.Inject
import kotlin.reflect.KParameter

/** HTTP binding as specified by [FeatureBinding]. */
internal class WebActionBinding @Inject constructor(
  private val action: Action,
  private val beforeCallBindings: Set<FeatureBinding>,
  private val afterCallBindings: Set<FeatureBinding>,
  private var requestBodyClaimer: FeatureBinding?,
  private val parameterClaimers: List<FeatureBinding>,
  private val responseBodyClaimer: FeatureBinding?,
  private val returnValueClaimer: FeatureBinding?
) {
  /** Returns the parameters for the call. */
  fun beforeCall(
    webAction: WebAction,
    httpCall: HttpCall,
    pathMatcher: Matcher
  ): List<Any?> {
    val execution = Execution(beforeCallBindings, webAction, httpCall, pathMatcher)
    execution.executeBeforeCall()
    return execution.parameters.toList()
  }

  /** Accepts the returned value from the call. */
  fun afterCall(
    webAction: WebAction,
    httpCall: HttpCall,
    pathMatcher: Matcher,
    returnValue: Any?
  ) {
    val execution = Execution(afterCallBindings, webAction, httpCall, pathMatcher)
    execution.returnValue = returnValue
    execution.executeAfterCall()
  }

  /** Performs bindings before (for parameters) or after a call (for the return value). */
  internal inner class Execution(
    private val bindings: Set<FeatureBinding>,
    override val webAction: WebAction,
    override val httpCall: HttpCall,
    override val pathMatcher: Matcher
  ) : FeatureBinding.Subject {
    internal val parameters = MutableList<Any?>(action.parameterTypes.size) { null }
    internal var returnValue: Any? = null

    /** Whichever binding is currently executing; used to enforce claims. */
    private var current: FeatureBinding? = null

    internal fun executeBeforeCall() {
      for (binding in bindings) {
        try {
          current = binding
          binding.beforeCall(this)
        } finally {
          current = null
        }
      }
    }

    internal fun executeAfterCall() {
      for (binding in bindings) {
        try {
          current = binding
          binding.afterCall(this)
        } finally {
          current = null
        }
      }
    }

    override fun setParameter(index: Int, value: Any?) {
      require(current == parameterClaimers[index]) { "parameter $index not claimed by $current" }
      parameters[index] = value
    }

    override fun setParameter(parameter: KParameter, value: Any?) =
        setParameter(parameter.index - 1, value)

    override fun takeRequestBody(): BufferedSource {
      require(current == requestBodyClaimer) { "request body not claimed by $current" }
      return httpCall.takeRequestBody()!!
    }

    override fun takeResponseBody(): BufferedSink {
      require(current == responseBodyClaimer) { "response body not claimed by $current" }
      return httpCall.takeResponseBody()!!
    }

    override fun takeReturnValue(): Any? {
      require(current == returnValueClaimer) { "return value not claimed by $current" }
      return returnValue
    }
  }

  class RealClaimer(val action: Action) : FeatureBinding.Claimer {
    /** Claims are taken by this placeholder until we get the actual FeatureBinding. */
    private object Placeholder : FeatureBinding {
      override fun beforeCall(subject: FeatureBinding.Subject) = throw AssertionError()
      override fun afterCall(subject: FeatureBinding.Subject) = throw AssertionError()
    }

    /** Claims by who made them. */
    private var requestBody: FeatureBinding? = null
    private val parameters = MutableList<FeatureBinding?>(action.parameterTypes.size) { null }
    private var responseBody: FeatureBinding? = null
    private var returnValue: FeatureBinding? = null

    private val beforeCallBindings = mutableSetOf<FeatureBinding>()
    private val afterCallBindings = mutableSetOf<FeatureBinding>()

    override fun claimRequestBody() {
      check(requestBody == null) { "already claimed by $requestBody" }
      check(action.dispatchMechanism != DispatchMechanism.GET
          && action.dispatchMechanism != DispatchMechanism.DELETE) {
        "cannot claim request body of ${action.dispatchMechanism.name}"
      }
      requestBody = Placeholder
    }

    override fun claimParameter(index: Int) {
      check(parameters[index] == null) { "already claimed by ${parameters[index]}" }
      parameters[index] = Placeholder
    }

    override fun claimParameter(parameter: KParameter) = claimParameter(parameter.index - 1)

    override fun claimResponseBody() {
      check(responseBody == null) { "already claimed by $responseBody" }
      responseBody = Placeholder
    }

    override fun claimReturnValue() {
      check(returnValue == null) { "already claimed by $returnValue" }
      check(action.hasReturnValue()) { "cannot claim the return value of $action which has none" }
      returnValue = Placeholder
    }

    /** Lock in the claims of a single binding. */
    internal fun commitClaims(factory: FeatureBinding.Factory, binding: FeatureBinding?) {
      if (requestBody == Placeholder) {
        check(binding != null) { "$factory returned null after making a claim" }
        requestBody = binding
      }

      var claimedParameter = false
      for (i in 0 until parameters.size) {
        if (parameters[i] == Placeholder) {
          check(binding != null) { "$factory returned null after making a claim" }
          parameters[i] = binding
          claimedParameter = true
        }
      }

      if (responseBody == Placeholder) {
        check(binding != null) { "$factory returned null after making a claim" }
        responseBody = binding
      }

      if (returnValue == Placeholder) {
        check(binding != null) { "$factory returned null after making a claim" }
        returnValue = binding
      }

      if (binding != null) {
        if (binding == returnValue) {
          // Bindings that claim the return value run after the call.
          afterCallBindings += binding
        }
        // Everything runs before the call.
        beforeCallBindings += binding
      }
    }

    /** Confirm everything that needs to be claimed is claimed. */
    internal fun newWebActionBinder(): WebActionBinding {
      // TODO(jwilson): require request body handlers. Add a DiscardRequestBodyFeatureHandler that
      //     discards the request body for method annotated with both @Post and @Get.
 @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
      if (false) {
        if (action.dispatchMechanism != DispatchMechanism.GET) {
          check(requestBody != null) { "$action request body not claimed" }
        }
      }

      val nonNullParameters = mutableListOf<FeatureBinding>()
      for (i in 0 until parameters.size) {
        nonNullParameters += checkNotNull(parameters[i]) {
          "$action parameter $i not claimed (did you forget @RequestBody ?)"
        }
      }

      if (action.dispatchMechanism != DispatchMechanism.WEBSOCKET) {
        check(responseBody != null) { "$action response body not claimed" }
      }

      if (action.hasReturnValue()) {
        check(returnValue != null) { "return value ${action.returnType} not claimed" }
      }

      return WebActionBinding(
          action,
          beforeCallBindings.toSet(),
          afterCallBindings.toSet(),
          requestBody,
          nonNullParameters.toList(),
          responseBody,
          returnValue
      )
    }
  }

  /** Creates feature bindings for use before and after a web call. */
  class Factory @Inject constructor(
    private val featureBindingFactories: List<FeatureBinding.Factory>
  ) {
    fun create(
      action: Action,
      pathPattern: PathPattern
    ): WebActionBinding {
      val claimer = RealClaimer(action)
      for (factory in featureBindingFactories) {
        val binding = factory.create(action, pathPattern, claimer)
        claimer.commitClaims(factory, binding)
      }
      return claimer.newWebActionBinder()
    }
  }
}
