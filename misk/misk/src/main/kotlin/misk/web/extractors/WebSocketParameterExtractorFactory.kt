package misk.web.extractors

import misk.web.PathPattern
import misk.web.Request
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import java.util.regex.Matcher
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

object WebSocketParameterExtractorFactory : ParameterExtractor.Factory {
  override fun create(
    function: KFunction<*>,
    parameter: KParameter,
    pathPattern: PathPattern
  ): ParameterExtractor? {
    if (parameter.type.classifier != WebSocket::class) return null

    return object : ParameterExtractor {
      override fun extract(
        webAction: WebAction,
        request: Request,
        pathMatcher: Matcher
      ): Any? {
        return request.takeWebSocket()
      }
    }
  }
}
