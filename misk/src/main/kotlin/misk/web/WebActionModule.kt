package misk.web

import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import misk.Interceptor
import misk.asAction
import misk.web.actions.WebAction
import misk.web.extractors.ParameterExtractor
import com.squareup.moshi.Types
import org.eclipse.jetty.http.HttpMethod
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

class WebActionModule<A : WebAction> private constructor(
    val webActionClass: KClass<A>,
    val member: KFunction<*>,
    val pathPattern: String,
    val httpMethod: HttpMethod
) : AbstractModule() {
    override fun configure() {
        val provider = getProvider(webActionClass.java)
        @Suppress("UNCHECKED_CAST")
        val binder: Multibinder<BoundAction<A, *>> = Multibinder.newSetBinder(
                binder(), TypeLiteral.get(Types.newParameterizedType(BoundAction::class.java, Types.subtypeOf(WebAction::class.java), Types.subtypeOf(Object::class.java)))) as Multibinder<BoundAction<A, *>>
        binder.addBinding().toProvider(BoundActionProvider(provider, member, pathPattern, httpMethod))
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
                    if (member !is KFunction<*>) throw IllegalArgumentException("expected $member to be a function")
                    if (result != null) throw IllegalArgumentException("multiple annotated methods in $webActionClass")
                    result = when (annotation) {
                        is Get -> WebActionModule(webActionClass, member, annotation.pathPattern, HttpMethod.GET)
                        is Post -> WebActionModule(webActionClass, member, annotation.pathPattern, HttpMethod.POST)
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

internal class BoundActionProvider<A : WebAction, R>(
    val provider: Provider<A>,
    val function: KFunction<R>,
    val pathPattern: String,
    val httpMethod: HttpMethod
) : Provider<BoundAction<A, *>> {

    @Inject lateinit var interceptorFactories: List<Interceptor.Factory>
    @Inject lateinit var parameterExtractorFactories: List<ParameterExtractor.Factory>

    override fun get(): BoundAction<A, *> {
        val action = function.asAction()

        val interceptors = ArrayList<Interceptor>()
        interceptorFactories.mapNotNullTo(interceptors) { it.create(action) }

        return BoundAction(provider, interceptors, parameterExtractorFactories, function,
                PathPattern.parse(pathPattern), httpMethod)
    }
}

val KType.typeLiteral: TypeLiteral<*>
    get() = TypeLiteral.get(javaType)

