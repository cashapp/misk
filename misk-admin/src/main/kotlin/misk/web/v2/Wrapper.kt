package misk.web.v2

import kotlinx.html.TagConsumer
import misk.tailwind.TailwindHtmlLayout

/** Default setup of HTML for a page including head and install of CSS/JS dependencies. */
fun TagConsumer<*>.HtmlLayout(appRoot: String, title: String, playCdn: Boolean, headBlock: TagConsumer<*>.() -> Unit = {}, hotReload: Boolean = true, bodyBlock: TagConsumer<*>.() -> Unit) {
  TailwindHtmlLayout(appRoot = appRoot, title = title, playCdn = playCdn, headBlock = headBlock, bodyBlock = bodyBlock, hotReload = hotReload)
}
