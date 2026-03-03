package misk.web.actions

import misk.web.dashboard.ValidWebEntry
import kotlin.reflect.KClass

/**
 * WebActionEntry
 *
 * A registration of a web action with optional configuration to customize.
 * @param actionClass: WebAction to multibind to WebServlet
 * @param url_path_prefix: Must match pattern requirements:
 *   - must begin with "/"
 *   - any number of non-whitespace characters (including additional path segments or "/")
 *   - must terminate with a non-"/" because rest of path will start with "/"
 */
internal class WebActionEntry(
  val actionClass: KClass<out WebAction>,
  url_path_prefix: String
) : ValidWebEntry(url_path_prefix = url_path_prefix)
