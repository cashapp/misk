package misk.web.extractors

import misk.Action
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.actions.WebSocket
import kotlin.reflect.KParameter

/** Binds parameters of type [WebSocket] to the call's web socket. */
internal class WebSocketFeatureBinding(
  private val parameter: KParameter
) : FeatureBinding {
  override fun beforeCall(subject: Subject) {
    val webSocket = subject.httpCall.takeWebSocket()
    subject.setParameter(parameter, webSocket)
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      val parameter = action.parameters.firstOrNull {
        it.type.classifier == WebSocket::class
      } ?: return null

      claimer.claimParameter(parameter)
      return WebSocketFeatureBinding(parameter)
    }
  }
}
