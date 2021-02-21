package misk.web.extractors

import misk.Action
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathParam
import misk.web.PathPattern
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation

/** Binds parameters annotated [PathParam] to URL path segments. */
internal class PathParamFeatureBinding private constructor(
  private val parameters: List<ParameterBinding>
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    for (element in parameters) {
      element.bind(subject)
    }
  }

  private class ParameterBinding(
    val patternIndex: Int,
    val parameter: KParameter,
    val converter: StringConverter
  ) {
    fun bind(subject: Subject) {
      val pathParam = subject.pathMatcher.group(patternIndex + 1)
      subject.setParameter(parameter, converter.invoke(pathParam))
    }
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      val bindings = action.parameters.mapNotNull { it.toParameterBinding(pathPattern) }
      if (bindings.isEmpty()) return null

      for (binding in bindings) {
        claimer.claimParameter(binding.parameter)
      }

      return PathParamFeatureBinding(bindings)
    }

    private fun KParameter.toParameterBinding(pathPattern: PathPattern): ParameterBinding? {
      val annotation = findAnnotation<PathParam>() ?: return null
      val name = if (annotation.value.isBlank()) name else annotation.value

      val patternIndex = pathPattern.variableNames.indexOf(name)
      if (patternIndex == -1) return null

      val converter = converterFor(type)
        ?: throw IllegalArgumentException("cannot convert path parameters to $type")

      return ParameterBinding(patternIndex, this, converter)
    }
  }
}
