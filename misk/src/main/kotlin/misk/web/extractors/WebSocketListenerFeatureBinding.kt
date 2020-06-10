package misk.web.extractors

import misk.Action
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import misk.web.actions.WebSocketListener

/** Binds return values of type [WebSocketListener]. */
internal class WebSocketListenerFeatureBinding : FeatureBinding {
  override fun afterCall(subject: Subject) {
    val webSocketListener = subject.takeReturnValue() as WebSocketListener
    subject.httpCall.initWebSocketListener(webSocketListener)
  }

  companion object Factory : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      if (action.returnType.classifier == WebSocketListener::class) {
        claimer.claimReturnValue()
        return WebSocketListenerFeatureBinding()
      }
      return null
    }
  }
}
