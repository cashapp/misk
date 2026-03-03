package misk.web.extractors

import misk.Action
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.RequestHeaders
import kotlin.reflect.KParameter

/** Binds parameters annotated [RequestHeaders] to request headers. */
internal class RequestHeadersFeatureBinding(
  val parameter: KParameter
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    subject.setParameter(parameter, subject.httpCall.requestHeaders)
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      val parameter = action.parameterAnnotatedOrNull<RequestHeaders>() ?: return null
      claimer.claimParameter(parameter)
      return RequestHeadersFeatureBinding(parameter)
    }
  }
}
