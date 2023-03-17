package com.squareup.exemplar.actions

import misk.web.Get
import misk.web.dashboard.AdminDashboardAccess
import javax.inject.Inject

class TestGetAction @Inject constructor() {
  @Get("/test/get")
  @AdminDashboardAccess
  fun get(): String {
    return "test"
  }
}
