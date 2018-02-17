package misk.web

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import misk.Interceptor
import misk.MiskDefault
import misk.asAction
import misk.inject.parameterizedType
import misk.inject.subtypeOf
import misk.inject.typeLiteral
import misk.web.actions.WebAction
import misk.web.extractors.ParameterExtractor
import misk.web.mediatype.MediaRange
import okhttp3.MediaType
import org.eclipse.jetty.http.HttpMethod
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation

class WebActionModule<A : WebAction> private constructor(
        private val webActionClass: KClass<A>
) : AbstractModule() {
    override fun configure() {
        @Suppress("UNCHECKED_CAST")
        val binder: Multibinder<BoundAction<A, *>> = Multibinder.newSetBinder(
                binder(),
                parameterizedType<BoundAction<*, *>>(subtypeOf<WebAction>(),
                        subtypeOf<Any>()).typeLiteral()
        ) as Multibinder<BoundAction<A, *>>

        // Find the function with Get or Post annotations. Only one such function is
        // allow, but may have both Get and Post annotations if the action can handle
        // both forms of HTTP method
        val actionFunctions = webActionClass.members.mapNotNull {
            if (it.findAnnotation<Get>() != null || it.findAnnotation<Post>() != null) {
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

        // Bind providers for each supported HTTP method
        val actionFunction = actionFunctions.first()
        val get = actionFunction.findAnnotation<Get>()
        if (get != null) {
            val getProvider = buildProvider(actionFunction, HttpMethod.GET, get.pathPattern)
            binder.addBinding().toProvider(getProvider)
        }

        val post = actionFunction.findAnnotation<Post>()
        if (post != null) {
            val postProvider = buildProvider(actionFunction, HttpMethod.POST, post.pathPattern)
            binder.addBinding().toProvider(postProvider)
        }
    }

    private fun buildProvider(function: KFunction<*>, httpMethod: HttpMethod, pathPattern: String):
            BoundActionProvider<A, *> {
        // NB(mmihic): The response media type may be ommitted; in this case only
        // generic return types (String, ByteString, ResponseBody, etc) are supported
        val responseContentType = function.responseContentType
        val acceptedContentTypes = function.acceptedContentTypes
        val provider = getProvider(webActionClass.java)
        return BoundActionProvider(
                provider,
                function,
                pathPattern,
                httpMethod,
                acceptedContentTypes,
                responseContentType
        )
    }

    companion object {
        inline fun <reified A : WebAction> create(): WebActionModule<A> = create(A::class)

        @JvmStatic
        fun <A : WebAction> create(webActionClass: Class<A>): WebActionModule<A> {
            return create(webActionClass.kotlin)
        }

        fun <A : WebAction> create(webActionClass: KClass<A>): WebActionModule<A> {
            return WebActionModule(webActionClass)
        }
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

internal class BoundActionProvider<A : WebAction, R>(
        val provider: Provider<A>,
        val function: KFunction<R>,
        private val pathPattern: String,
        private val httpMethod: HttpMethod,
        private val acceptedContentTypes: List<MediaRange>,
        private val responseContentType: MediaType?
) : Provider<BoundAction<A, *>> {

    @Inject
    private lateinit var userProvidedInterceptorFactories: List<Interceptor.Factory>

    @Inject
    @JvmSuppressWildcards
    @MiskDefault
    private lateinit var miskInterceptorFactories: Set<Interceptor.Factory>

    @Inject
    private lateinit var parameterExtractorFactories: List<ParameterExtractor.Factory>

    override fun get(): BoundAction<A, *> {
        val action = function.asAction()

        val interceptors = ArrayList<Interceptor>()
        // Ensure that default interceptors are called before any user provided interceptors
        miskInterceptorFactories.mapNotNullTo(interceptors) { it.create(action) }
        userProvidedInterceptorFactories.mapNotNullTo(interceptors) { it.create(action) }

        return BoundAction(provider, interceptors, parameterExtractorFactories, function,
                PathPattern.parse(pathPattern), httpMethod, acceptedContentTypes,
                responseContentType)
    }
}
