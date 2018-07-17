package misk.web

import com.google.inject.Injector
import com.google.inject.util.Providers
import misk.ApplicationInterceptor
import misk.MiskDefault
import misk.asAction
import misk.web.actions.WebAction
import misk.web.extractors.ParameterExtractor
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import org.eclipse.jetty.http.HttpMethod
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation

@Singleton
internal class WebActionFactory {
  @Inject lateinit var injector: Injector

  @Inject
  lateinit var userProvidedApplicationInterceptorFactories: List<ApplicationInterceptor.Factory>

  @Inject lateinit var userProvidedNetworkInterceptorFactories: List<NetworkInterceptor.Factory>

  @Inject @MiskDefault lateinit var miskInterceptorFactories: List<NetworkInterceptor.Factory>

  @Inject lateinit var parameterExtractorFactories: List<ParameterExtractor.Factory>

  /** Returns the bound actions for `webActionClass`. */
  fun <A : WebAction> newBoundAction(
    webActionClass: KClass<A>,
    pathPrefix: String = ""
  ): List<BoundAction<A, *>> {
    // Find the function with Get, Post, or ConnectWebSocket annotation. Only one such function is
    // allowed.
    val actionFunctions = webActionClass.members.mapNotNull {
      if (it.findAnnotation<Get>() != null ||
          it.findAnnotation<Post>() != null ||
          it.findAnnotation<ConnectWebSocket>() != null) {
        it as? KFunction<*>
            ?: throw IllegalArgumentException("expected $it to be a function")
      } else null
    }

    require(actionFunctions.isNotEmpty()) {
      "no Get or Post annotations on ${webActionClass.simpleName}"
    }

    require(actionFunctions.size == 1) {
      val actionFunctionNames = actionFunctions.joinToString(", ") { it.name }
      "multiple annotated methods on ${webActionClass.simpleName}: $actionFunctionNames"
    }

    // Bind providers for each supported HTTP method.
    val actionFunction = actionFunctions.first()
    val get = actionFunction.findAnnotation<Get>()

    val connectWebSocket = actionFunction.findAnnotation<ConnectWebSocket>()
    val post = actionFunction.findAnnotation<Post>()

    // TODO(adrw) fix this using first provider below so that WebAction::class or WebAction can be passed in
    val provider = Providers.of(theInstance)
    val provider = injector.getProvider(webActionClass.java)

    val result: MutableList<BoundAction<A, *>> = mutableListOf()

    if (get != null) {
      result += newBoundAction(provider, actionFunction, HttpMethod.GET,
          pathPrefix + get.pathPattern, false)
    }
    if (connectWebSocket != null) {
      result += newBoundAction(provider, actionFunction, HttpMethod.GET,
          connectWebSocket.pathPattern, true)
    }
    if (post != null) {
      result += newBoundAction(provider, actionFunction, HttpMethod.POST,
          pathPrefix + post.pathPattern, false)
    }

    return result
  }

  private fun <A : WebAction> newBoundAction(
    provider: Provider<A>,
    function: KFunction<*>,
    httpMethod: HttpMethod,
    pathPattern: String,
    isConnectWebSocketAction: Boolean
  ): BoundAction<A, *> {
    // NB: The response media type may be omitted; in this case only generic return types (String,
    // ByteString, ResponseBody, etc) are supported
    val responseContentType = function.responseContentType
    val acceptedContentTypes = function.acceptedContentTypes

    val action = function.asAction()

    val networkInterceptors = ArrayList<NetworkInterceptor>()
    // Ensure that default interceptors are called before any user provided interceptors
    miskInterceptorFactories.mapNotNullTo(networkInterceptors) { it.create(action) }
    userProvidedNetworkInterceptorFactories.mapNotNullTo(networkInterceptors) { it.create(action) }

    val applicationInterceptors = ArrayList<ApplicationInterceptor>()
    userProvidedApplicationInterceptorFactories.mapNotNullTo(applicationInterceptors) {
      it.create(action)
    }

    return BoundAction(provider, networkInterceptors, applicationInterceptors,
        parameterExtractorFactories, function, PathPattern.parse(pathPattern), httpMethod,
        acceptedContentTypes, responseContentType, isConnectWebSocketAction)
  }
}

private val KFunction<*>.acceptedContentTypes: List<MediaRange>
  get() = findAnnotation<RequestContentType>()?.value?.flatMap {
    MediaRange.parseRanges(it)
  }?.toList() ?: listOf(MediaRange.ALL_MEDIA)

private val KFunction<*>.responseContentType: MediaType?
  get() = findAnnotation<ResponseContentType>()?.value?.let {
    MediaType.parse(it)
  }
