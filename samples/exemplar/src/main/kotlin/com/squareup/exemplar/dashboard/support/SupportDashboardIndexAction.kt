package com.squareup.exemplar.dashboard.support

import kotlinx.html.h1
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class SupportDashboardIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {
  @Get("/support/")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String = dashboardPageLayout
    .newBuilder()
    .build { appName, _, _ ->
      h1 { +"""This is a custom Support dashboard for $appName""" }
    }
}
