package com.squareup.exemplar.dashboard.frontend

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.h1
import kotlinx.html.p
import misk.config.AppName
import misk.hotwire.buildHtml
import misk.turbo.turbo_frame
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.HtmlLayout
import misk.web.mediatype.MediaTypes

/** Example page from Tailwind UI https://tailwindui.com/components/ecommerce/page-examples/storefront-pages */
@Singleton
class SimplePage @Inject constructor(@AppName private val appName: String) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String {
    return buildHtml {
      HtmlLayout(appRoot = "/app", title = "$appName frontend", playCdn = false) {
        turbo_frame(id = "tab") {
          h1 { +"""Example Header""" }
          p { +"""Example Paragraph""" }
        }
      }
    }
  }

  companion object {
    const val PATH = "/ui/example/simple"
  }
}
