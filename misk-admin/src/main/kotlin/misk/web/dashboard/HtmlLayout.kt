package misk.web.dashboard

import kotlinx.html.TagConsumer
import misk.tailwind.TailwindHtmlLayout

/** Default setup of HTML for a page including head and install of CSS/JS dependencies. */
fun TagConsumer<*>.HtmlLayout(appRoot: String, title: String, playCdn: Boolean, appCssPath: String? = null, headBlock: TagConsumer<*>.() -> Unit = {}, bodyBlock: TagConsumer<*>.() -> Unit) {
  TailwindHtmlLayout(appRoot = appRoot, title = title, playCdn = playCdn, appCssPath = appCssPath, headBlock = headBlock, bodyBlock = bodyBlock)
}
