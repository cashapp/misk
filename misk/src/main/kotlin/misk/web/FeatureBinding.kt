package misk.web

import misk.Action
import misk.web.actions.WebAction
import okio.BufferedSink
import okio.BufferedSource
import java.util.regex.Matcher
import kotlin.reflect.KParameter

/**
 * Binds an HTTP call to a [WebAction] function.
 *
 * Each web action has several features:
 *
 *  * An optional HTTP request body. Some HTTP methods (GET, HEAD) don't have a request body.
 *  * An HTTP response body. Always present but possibly empty.
 *  * Zero or more function parameters.
 *  * An optional function return value. Functions that return [Unit] do not have a return value.
 *
 * Each bindings claims one or more features that it is responsible for. Some examples:
 *
 *  * A path parameter binding sets parameters 1 and 2 by parsing the request path.
 *  * The request marshaller decodes the request body and sets it as @RequestBody parameter 3.
 *  * The response marshaller takes the return value and encodes it as the response stream.
 *
 * A binding can claim multiple features. [beforeCall] is invoked if any features are claimed, and
 * [afterCall] is only invoked if the return value is claimed.
 *
 * ## Creating Bindings
 *
 * Each factory is executed once for each endpoint at service startup time. Factories should
 * interrogate the function and claim whichever features that intend to bind. This happens early so
 * Misk can validate that every feature is bound exactly once.
 *
 * Factories that return null must make no claims. If no claims are made then no binding is
 * executed.
 *
 * Misk will validate that every feature is claimed exactly once. If a feature is unclaimed that is
 * a fatal error and the service will not be started.
 *
 * ## Binding Execution
 *
 * Once a functions bindings have been created and validated, each binding will be executed once
 * before and once after every each HTTP call. In this method it must bind the features it claimed
 * by providing parameters, reading the request body, writing the response body, or taking the
 * return value.
 */
interface FeatureBinding {
  fun beforeCall(subject: Subject) {}
  fun afterCall(subject: Subject) {}

  interface Subject {
    val webAction: WebAction
    val httpCall: HttpCall
    val pathMatcher: Matcher
    fun setParameter(parameter: KParameter, value: Any?)
    fun setParameter(index: Int, value: Any?)
    fun takeRequestBody(): BufferedSource
    fun takeResponseBody(): BufferedSink
    fun takeReturnValue(): Any?
  }

  interface Factory {
    fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding?
  }

  interface Claimer {
    fun claimRequestBody()
    fun claimParameter(index: Int)
    fun claimParameter(parameter: KParameter)
    fun claimResponseBody()
    fun claimReturnValue()
  }
}
