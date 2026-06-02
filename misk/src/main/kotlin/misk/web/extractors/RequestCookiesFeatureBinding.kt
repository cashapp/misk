package misk.web.extractors

import kotlin.reflect.KParameter
import misk.Action
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.RequestCookies

/** Binds parameters annotated [RequestCookies] to request cookies. */
internal class RequestCookiesFeatureBinding(val parameter: KParameter) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    subject.setParameter(parameter, subject.httpCall.cookies)
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer,
      stringConverterFactories: List<StringConverter.Factory>,
    ): FeatureBinding? {
      val parameter = action.parameterAnnotatedOrNull<RequestCookies>() ?: return null
      claimer.claimParameter(parameter)
      return RequestCookiesFeatureBinding(parameter)
    }
  }
}
