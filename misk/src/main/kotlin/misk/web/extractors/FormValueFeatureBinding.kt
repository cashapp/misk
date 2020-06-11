package misk.web.extractors

import misk.Action
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.FormValue
import misk.web.PathPattern
import kotlin.reflect.KClass
import kotlin.reflect.KParameter

/** Binds parameters annotated [FormValue] to the request body using form encoding. */
internal class FormValueFeatureBinding<T : Any>(
  private val parameter: KParameter,
  private val formAdapter: FormAdapter<T>
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    val requestBody = subject.httpCall.takeRequestBody()!!
    val formData = FormData.decode(requestBody)
    subject.setParameter(parameter, formAdapter.fromFormData(formData))
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      val parameter = action.parameterAnnotatedOrNull<FormValue>() ?: return null
      if (parameter.type.classifier !is KClass<*>) return null

      val kClass = parameter.type.classifier as KClass<*>
      val formAdapter = FormAdapter.create(kClass) ?: return null
      claimer.claimRequestBody()
      claimer.claimParameter(parameter)
      return FormValueFeatureBinding(parameter, formAdapter)
    }
  }
}
