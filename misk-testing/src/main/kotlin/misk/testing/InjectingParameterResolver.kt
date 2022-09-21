package misk.testing

import com.google.inject.Injector
import com.google.inject.Key
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class InjectingParameterResolver : ParameterResolver {
  override fun supportsParameter(
    parameterContext: ParameterContext,
    extensionContext: ExtensionContext
  ): Boolean = true

  override fun resolveParameter(
    parameterContext: ParameterContext,
    extensionContext: ExtensionContext
  ): Any {
    val injector = extensionContext.retrieve<Injector>("injector")

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
}
