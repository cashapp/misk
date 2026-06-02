package com.squareup.exemplar.dashboard.support

import com.squareup.exemplar.dashboard.SupportDashboardAccess
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.h1
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout

@Singleton
class SupportBravoIndexAction @Inject constructor(private val dashboardPageLayout: DashboardPageLayout) : WebAction {
  @Get("/support/bravo/")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @SupportDashboardAccess
  fun get(): String =
    dashboardPageLayout.newBuilder().build { appName, _, _ ->
      h1 { +"""This is a custom Hotwire tab for Support dashboard on $appName""" }
    }
}
