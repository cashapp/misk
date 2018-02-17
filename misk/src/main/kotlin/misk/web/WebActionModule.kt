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
    val webActionClass: KClass<A>,
    val member: KFunction<*>,
    val pathPattern: String,
    val httpMethod: HttpMethod,
    val acceptedContentTypes: List<MediaRange>,
    val responseContentType: MediaType?
) : AbstractModule() {
  override fun configure() {
    val provider = getProvider(webActionClass.java)
    @Suppress("UNCHECKED_CAST")
    val binder: Multibinder<BoundAction<A, *>> = Multibinder.newSetBinder(
        binder(),
        parameterizedType<BoundAction<*, *>>(
            subtypeOf<WebAction>(),
            subtypeOf<Any>()
        ).typeLiteral()
    ) as Multibinder<BoundAction<A, *>>
    binder.addBinding()
        .toProvider(
            BoundActionProvider(
                provider, member, pathPattern, httpMethod,
                acceptedContentTypes, responseContentType
            )
        )
  }

  companion object {
    inline fun <reified A : WebAction> create(): WebActionModule<A> = create(A::class)

    @JvmStatic
    fun <A : WebAction> create(webActionClass: Class<A>): WebActionModule<A> {
      return create(webActionClass.kotlin)
    }

    fun <A : WebAction> create(webActionClass: KClass<A>): WebActionModule<A> {
      var result: WebActionModule<A>? = null

      for (member in webActionClass.members) {
        for (annotation in member.annotations) {
          if (annotation !is Get && annotation !is Post) continue
          if (member !is KFunction<*>) throw IllegalArgumentException(
              "expected $member to be a function"
          )
          if (result != null) throw IllegalArgumentException(
              "multiple annotated methods in $webActionClass"
          )

          // NB(mmihic): The response media type may be ommitted; in this case only
          // generic return types (String, ByteString, ResponseBody, etc) are supported
          val responseContentType = member.responseContentType
          val acceptedContentTypes = member.acceptedContentTypes

          result = when (annotation) {
            is Get -> WebActionModule(
                webActionClass, member, annotation.pathPattern,
                HttpMethod.GET, acceptedContentTypes, responseContentType
            )
            is Post -> WebActionModule(
                webActionClass, member, annotation.pathPattern,
                HttpMethod.POST, acceptedContentTypes, responseContentType
            )
            else -> throw AssertionError()
          }
        }
      }
      if (result == null) {
        throw IllegalArgumentException("no annotated methods in $webActionClass")
      }
      return result
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
    val pathPattern: String,
    val httpMethod: HttpMethod,
    val acceptedContentTypes: List<MediaRange>,
    val responseContentType: MediaType?
) : Provider<BoundAction<A, *>> {

  @Inject
  lateinit var userProvidedInterceptorFactories: List<Interceptor.Factory>
  @Inject
  @JvmSuppressWildcards
  @MiskDefault
  lateinit var miskInterceptorFactories: Set<Interceptor.Factory>
  @Inject
  lateinit var parameterExtractorFactories: List<ParameterExtractor.Factory>

  override fun get(): BoundAction<A, *> {
    val action = function.asAction()

    val interceptors = ArrayList<Interceptor>()
    // Ensure that default interceptors are called before any user provided interceptors
    miskInterceptorFactories.mapNotNullTo(interceptors) { it.create(action) }
    userProvidedInterceptorFactories.mapNotNullTo(interceptors) { it.create(action) }

    return BoundAction(
        provider, interceptors, parameterExtractorFactories, function,
        PathPattern.parse(pathPattern), httpMethod, acceptedContentTypes,
        responseContentType
    )
  }
}
