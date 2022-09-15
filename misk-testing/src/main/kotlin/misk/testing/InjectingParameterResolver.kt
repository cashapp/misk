package misk.testing

import com.google.inject.Injector
import com.google.inject.Key
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver
import kotlin.reflect.KClass

class InjectingParameterResolver : ParameterResolver {
  override fun supportsParameter(
    parameterContext: ParameterContext,
    extensionContext: ExtensionContext
  ): Boolean = methodHasInjectAnnotation(extensionContext)

  override fun resolveParameter(
    parameterContext: ParameterContext,
    extensionContext: ExtensionContext
  ): Any {
    val injector = extensionContext.retrieve<Injector>("injector")

    if (methodHasInjectAnnotation(extensionContext)) {
      return if (parameterContext.parameter.annotations.isNotEmpty()) {
        injector.getInstance(
          Key.get(
            parameterContext.parameter.parameterizedType,
            parameterContext.parameter.annotations.first()
          )
        )
      } else {
        injector.getInstance(Key.get(parameterContext.parameter.parameterizedType))
      }
    }

    throw ParameterResolutionException("Unexpected state")
  }

  private fun methodHasInjectAnnotation(extensionContext: ExtensionContext) = INJECT_CLASSES
    .union(extensionContext.testMethod.orElse(null)
      ?.annotations?.map { it::class }
      ?: emptyList()
    )
    .isNotEmpty()

  companion object {
    val INJECT_CLASSES: List<KClass<out Annotation>> = listOf(
      javax.inject.Inject::class,
      com.google.inject.Inject::class
    )
  }
}
