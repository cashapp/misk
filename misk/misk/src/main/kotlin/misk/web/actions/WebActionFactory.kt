package misk.web.actions

import com.google.inject.Injector
import com.google.inject.Provider
import com.squareup.wire.WireRpc
import misk.Action
import misk.ApplicationInterceptor
import misk.MiskDefault
import misk.asAction
import misk.scope.ActionScope
import misk.web.BoundAction
import misk.web.ConnectWebSocket
import misk.web.DispatchMechanism
import misk.web.Get
import misk.web.NetworkInterceptor
import misk.web.PathPattern
import misk.web.Post
import misk.web.WebActionBinding
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions

@Singleton
internal class WebActionFactory @Inject constructor(
  private val injector: Injector,
  private val userProvidedApplicationInterceptorFactories: List<ApplicationInterceptor.Factory>,
  private val userProvidedNetworkInterceptorFactories: List<NetworkInterceptor.Factory>,
  @MiskDefault private val miskNetworkInterceptorFactories: List<NetworkInterceptor.Factory>,
  @MiskDefault private val miskApplicationInterceptorFactories: List<ApplicationInterceptor.Factory>,
  private val webActionBindingFactory: WebActionBinding.Factory,
  private val scope: ActionScope
) {

  /** Returns the bound actions for `webActionClass`. */
  fun <A : WebAction> newBoundAction(
    webActionClass: KClass<A>,
    pathPrefix: String = "/"
  ): List<BoundAction<A>> {
    // Find the function with Get, Post, or ConnectWebSocket annotation. Only one such function is
    // allowed.
    val actionFunctions = webActionClass.functions.mapNotNull {
      if (it.findAnnotationWithOverrides<Get>() != null ||
          it.findAnnotationWithOverrides<Post>() != null ||
          it.findAnnotationWithOverrides<ConnectWebSocket>() != null ||
          it.findAnnotationWithOverrides<WireRpc>() != null) {
        it as? KFunction<*>
            ?: throw IllegalArgumentException("expected $it to be a function")
      } else null
    }

    require(actionFunctions.isNotEmpty()) {
      "no @Get, @Post, @ConnectWebSocket, or @Grpc annotations on ${webActionClass.simpleName}"
    }

    require(actionFunctions.size == 1) {
      val actionFunctionNames = actionFunctions.joinToString(", ") { it.name }
      "multiple annotated methods on ${webActionClass.simpleName}: $actionFunctionNames"
    }

    // Bind providers for each supported HTTP method.
    val actionFunction = actionFunctions.first()
    val get = actionFunction.findAnnotationWithOverrides<Get>()
    val post = actionFunction.findAnnotationWithOverrides<Post>()
    val connectWebSocket = actionFunction.findAnnotationWithOverrides<ConnectWebSocket>()
    val grpc = actionFunction.findAnnotationWithOverrides<WireRpc>()

    // TODO(adrw) fix this using first provider below so that WebAction::class or WebAction can be passed in
    // val provider = Providers.of(theInstance)

    val provider = injector.getProvider(webActionClass.java)

    val result: MutableList<BoundAction<A>> = mutableListOf()

    require(pathPrefix.last() == '/')
    val effectivePrefix = pathPrefix.dropLast(1)

    if (get != null) {
      collectBoundActions(result, provider, actionFunction,
          effectivePrefix + get.pathPattern, DispatchMechanism.GET)
    }
    if (post != null) {
      collectBoundActions(result, provider, actionFunction,
          effectivePrefix + post.pathPattern, DispatchMechanism.POST)
    }
    if (connectWebSocket != null) {
      collectBoundActions(result, provider, actionFunction,
          effectivePrefix + connectWebSocket.pathPattern, DispatchMechanism.WEBSOCKET)
    }
    if (grpc != null) {
      collectBoundActions(result, provider, actionFunction,
          effectivePrefix + grpc.path, DispatchMechanism.GRPC)
    }

    return result
  }

  private fun <A : WebAction> collectBoundActions(
    result: MutableList<BoundAction<A>>,
    provider: Provider<A>,
    function: KFunction<*>,
    pathPattern: String,
    dispatchMechanism: DispatchMechanism
  ) {
    // NB: The response media type may be omitted; in this case only generic return types (String,
    // ByteString, ResponseBody, etc) are supported
    val action = function.asAction(dispatchMechanism)
    result += newBoundAction(provider, pathPattern, action)

    // If we can create a synthetic action with a different media type, do it. This means all
    // protobuf actions are also published as JSON actions.
    val jsonVariant = transformActionIntoJson(action)
    if (jsonVariant != null) {
      result += newBoundAction(provider, pathPattern, jsonVariant)
    }
  }

  /**
   * Returns a copy of [action] that pretends JSON was the request and response content types.
   * Returns null if neither of the input action's content types were protobuf.
   */
  private fun transformActionIntoJson(action: Action): Action? {
    var jsonVariant = action

    if (action.acceptedMediaRanges == protobufMediaRanges) {
      jsonVariant = jsonVariant.copy(acceptedMediaRanges = jsonMediaRanges)
    }
    if (action.responseContentType == protobufMediaType) {
      jsonVariant = jsonVariant.copy(responseContentType = jsonMediaType)
    }

    if (jsonVariant === action) return null
    return jsonVariant
  }

  private fun <A : WebAction> newBoundAction(
    provider: Provider<A>,
    pathPattern: String,
    action: Action
  ): BoundAction<A> {
    // Ensure that default interceptors are called before any user provided interceptors
    val networkInterceptors =
        miskNetworkInterceptorFactories.mapNotNull { it.create(action) } +
            userProvidedNetworkInterceptorFactories.mapNotNull { it.create(action) }

    val applicationInterceptors =
        miskApplicationInterceptorFactories.mapNotNull { it.create(action) } +
            userProvidedApplicationInterceptorFactories.mapNotNull { it.create(action) }

    val parsedPathPattern = PathPattern.parse(pathPattern)

    val webActionBinding = webActionBindingFactory.create(action, parsedPathPattern)

    return BoundAction(
        scope,
        provider,
        networkInterceptors,
        applicationInterceptors,
        webActionBinding,
        parsedPathPattern,
        action
    )
  }

  companion object {
    private val protobufMediaRanges = MediaRange.parseRanges(MediaTypes.APPLICATION_PROTOBUF)
    private val jsonMediaRanges = MediaRange.parseRanges(MediaTypes.APPLICATION_JSON)
    private val protobufMediaType = MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE
    private val jsonMediaType = MediaTypes.APPLICATION_JSON_MEDIA_TYPE
  }
}
