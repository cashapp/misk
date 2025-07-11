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
import misk.web.Delete
import misk.web.DispatchMechanism
import misk.web.Get
import misk.web.Grpc
import misk.web.NetworkInterceptor
import misk.web.Patch
import misk.web.PathPattern
import misk.web.Post
import misk.web.Put
import misk.web.RequestBody
import misk.web.ResponseContentType
import misk.web.WebActionBinding
import misk.web.WebActionSeedDataTransformerFactory
import misk.web.interceptors.BeforeContentEncoding
import misk.web.interceptors.ForContentEncoding
import misk.web.mediatype.MediaRange
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType.Companion.toMediaType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.ProtoDocumentationProvider
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

@Singleton
internal class WebActionFactory @Inject constructor(
  private val injector: Injector,
  @BeforeContentEncoding
  private val beforeContentEncodingNetworkInterceptorFactories: List<NetworkInterceptor.Factory>,
  @ForContentEncoding
  private val forContentEncodingNetworkInterceptorFactories: List<NetworkInterceptor.Factory>,
  private val userProvidedApplicationInterceptorFactories: List<ApplicationInterceptor.Factory>,
  private val userProvidedNetworkInterceptorFactories: List<NetworkInterceptor.Factory>,
  @MiskDefault private val miskNetworkInterceptorFactories: List<NetworkInterceptor.Factory>,
  @MiskDefault
  private val miskApplicationInterceptorFactories: List<ApplicationInterceptor.Factory>,
  private val webActionBindingFactory: WebActionBinding.Factory,
  private val scope: ActionScope,
  private val actionScopeSeedDataTransformerFactories: List<WebActionSeedDataTransformerFactory>,
  private val documentationProvider: Optional<ProtoDocumentationProvider>
) {

  /** Returns the bound actions for `webActionClass`. */
  fun <A : WebAction> newBoundAction(
    webActionClass: KClass<A>,
    pathPrefix: String = "/"
  ): List<BoundAction<A>> {
    // Find the function with Get, Post, Put, Delete or ConnectWebSocket annotation.
    // Only one such function is allowed.
    val functionsWithOverrides = webActionClass.functions.map { it.withOverrides() }

    val actionFunctions = functionsWithOverrides.mapNotNull {
      if (it.findAnnotation<Get>() != null ||
        it.findAnnotation<Post>() != null ||
        it.findAnnotation<Patch>() != null ||
        it.findAnnotation<Put>() != null ||
        it.findAnnotation<Grpc>() != null ||
        it.findAnnotation<Delete>() != null ||
        it.findAnnotation<ConnectWebSocket>() != null ||
        it.findAnnotation<WireRpc>() != null
      ) {
        it as? KFunction<*>
          ?: throw IllegalArgumentException("expected $it to be a function")
      } else null
    }

    require(actionFunctions.isNotEmpty()) {
      "no @Get, @Post, @Patch, @Put, @Delete, @ConnectWebSocket, or @WireRpc annotations " +
        "on ${webActionClass.simpleName}"
    }

    // TODO(adrw) fix this using first provider below so that WebAction::class or WebAction can be passed in
    // val provider = Providers.of(theInstance)

    val provider = injector.getProvider(webActionClass.java)

    val result: MutableList<BoundAction<A>> = mutableListOf()

    require(pathPrefix.last() == '/')
    val effectivePrefix = pathPrefix.dropLast(1)

    actionFunctions.forEach { actionFunction ->
      val get = actionFunction.findAnnotation<Get>()
      val post = actionFunction.findAnnotation<Post>()
      val patch = actionFunction.findAnnotation<Patch>()
      val put = actionFunction.findAnnotation<Put>()
      val delete = actionFunction.findAnnotation<Delete>()
      val webActionGrpc = actionFunction.findAnnotation<Grpc>()
      val connectWebSocket = actionFunction.findAnnotation<ConnectWebSocket>()
      val grpc = actionFunction.findAnnotation<WireRpc>()

      if (get != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + get.pathPattern, DispatchMechanism.GET
        )
      }
      if (post != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + post.pathPattern, DispatchMechanism.POST
        )
      }
      if (patch != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + patch.pathPattern, DispatchMechanism.PATCH
        )
      }
      if (put != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + put.pathPattern, DispatchMechanism.PUT
        )
      }
      if (delete != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + delete.pathPattern, DispatchMechanism.DELETE
        )
      }
      if (webActionGrpc != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + webActionGrpc.pathPattern, DispatchMechanism.GRPC
        )
      }
      if (connectWebSocket != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + connectWebSocket.pathPattern, DispatchMechanism.WEBSOCKET
        )
      }
      if (grpc != null) {
        collectBoundActions(
          result, provider, actionFunction,
          effectivePrefix + grpc.path, DispatchMechanism.GRPC
        )
      }
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
    val responseContentTypes = function.findAnnotation<ResponseContentType>()
      ?.value
      ?.toList()
      // We have to have an element in the list to be able to flatMap over it below.
      ?: listOf(null)

    val actions = responseContentTypes.flatMap { responseContentType ->
      // NB: The response media type may be omitted; in this case only generic return types (String,
      // ByteString, ResponseBody, etc) are supported
      val action = function.asAction(dispatchMechanism, responseContentType?.toMediaType())

      // If we can create a synthetic action with a different media type, do it. This means all
      // protobuf actions are also published as JSON actions.
      val jsonVariant = transformActionIntoJson(action)

      listOfNotNull(
        newBoundAction(provider, pathPattern, action),
        jsonVariant?.let { newBoundAction(provider, pathPattern, jsonVariant) },
      )
    }

    // Because we create a synthetic JSON action for each protobuf action, we need to dedupe the
    // actions for the case where a user explicitly annotates an endpoint to service both JSON and
    // Protobuf, to avoid having the synthetic JSON action and the "real" one from the annotation.
    result += actions.distinctBy { it.action }
  }

  /**
   * Returns a copy of [action] that pretends JSON was the request and response content types.
   * Returns null if neither of the input action's content types were protobuf.
   */
  private fun transformActionIntoJson(action: Action): Action? {
    // Map gRPC actions to the equivalent JSON actions. This changes the dispatch mechanism from
    // gRPC to POST which prevents gRPC framing from being used.
    if (action.dispatchMechanism == DispatchMechanism.GRPC && action.parameters.size == 1) {
      val mappedParameters = action.parameters.toMutableList()
      mappedParameters[0] = mappedParameters[0].withAnnotations(listOf(requestBodyAnnotation()))
      return action.copy(
        dispatchMechanism = DispatchMechanism.POST,
        acceptedMediaRanges = jsonMediaRanges,
        responseContentType = jsonMediaType,
        parameters = mappedParameters
      )
    }

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
    // Ensure that default interceptors are called before any user provided interceptors.
    val networkInterceptors =
      beforeContentEncodingNetworkInterceptorFactories.mapNotNull { it.create(action) } +
        forContentEncodingNetworkInterceptorFactories.mapNotNull { it.create(action) } +
        miskNetworkInterceptorFactories.mapNotNull { it.create(action) } +
        userProvidedNetworkInterceptorFactories.mapNotNull { it.create(action) }

    val applicationInterceptors =
      miskApplicationInterceptorFactories.mapNotNull { it.create(action) } +
        userProvidedApplicationInterceptorFactories.mapNotNull { it.create(action) }

    val parsedPathPattern = PathPattern.parse(pathPattern)

    val webActionBinding = webActionBindingFactory.create(action, parsedPathPattern)

    val httpActionScopeSeedDataInterceptors =
      actionScopeSeedDataTransformerFactories.mapNotNull {
        it.create(
          parsedPathPattern,
          action,
        )
      }

    return BoundAction(
      scope,
      provider,
      networkInterceptors,
      applicationInterceptors,
      webActionBinding,
      httpActionScopeSeedDataInterceptors,
      documentationProvider.getOrNull(),
      parsedPathPattern,
      action,
    )
  }

  /** Returns a copy of this that overrides the current annotations with [annotations]. */
  private fun KParameter.withAnnotations(annotations: List<Annotation>): KParameter {
    return object : KParameter by this {
      override val annotations: List<Annotation> = annotations
    }
  }

  /** Kotlin doesn't have a way create an instance of an annotation so we get one reflectively. */
  private fun requestBodyAnnotation(): Annotation {
    return WebActionFactory::class.functions
      .first { it.name == "requestBodyAnnotationFun" }
      .parameters[1]
      .annotations[0]
  }

  @Suppress("unused", "UNUSED_PARAMETER") // Used reflectively in the function above.
  private fun requestBodyAnnotationFun(@RequestBody any: Any) {
    error("unexpected call")
  }

  companion object {
    private val protobufMediaRanges = MediaRange.parseRanges(MediaTypes.APPLICATION_PROTOBUF)
    private val grpcMediaRanges = MediaRange.parseRanges(MediaTypes.APPLICATION_GRPC)
    private val jsonMediaRanges = MediaRange.parseRanges(MediaTypes.APPLICATION_JSON)
    private val protobufMediaType = MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE
    private val grpcMediaType = MediaTypes.APPLICATION_GRPC_MEDIA_TYPE
    private val jsonMediaType = MediaTypes.APPLICATION_JSON_MEDIA_TYPE
  }
}
