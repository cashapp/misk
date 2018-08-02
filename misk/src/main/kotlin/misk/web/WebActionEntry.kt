package misk.web

import misk.web.actions.WebAction
import kotlin.reflect.KClass

/**
 * WebActionEntry
 *
 * A registration of a web action with optional configuration to customize.
 * @param actionClass: WebAction to multibind to WebServlet
 * @param pathPrefix: defaults to "" empty string. If not empty, must match pattern requirements:
 *   - must begin with "/"
 *   - any number of non-whitespace characters (including additional path segments or "/")
 *   - must terminate with a non-"/"
 */
data class WebActionEntry(
  val actionClass: KClass<out WebAction>,
  val pathPrefix: String = ""
) {
  init {
    require(pathPrefix.matches(Regex("(/[^/]+)*")) && !pathPrefix.startsWith("/api")) {
      "unexpected path prefix: $pathPrefix"
    }
  }
}

inline fun <reified T : WebAction> WebActionEntry(
  pathPrefix: String = ""
): WebActionEntry {
  return WebActionEntry(T::class, pathPrefix)
}
