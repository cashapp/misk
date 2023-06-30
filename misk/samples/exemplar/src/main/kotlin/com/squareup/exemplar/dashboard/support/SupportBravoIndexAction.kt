package com.squareup.exemplar.dashboard.support

import kotlinx.html.h1
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupportBravoIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout
) : WebAction {
  @Get("/support/bravo/")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String = dashboardPageLayout
    .newBuilder()
    .build { appName, _, _ ->
      h1 { +"""This is a custom Hotwire tab for Support dashboard on $appName""" }
    }
}
