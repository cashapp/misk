package com.squareup.chat.actions

import com.squareup.chat.healthchecks.ManualHealthCheck
import misk.web.Post
import misk.web.QueryParam
import misk.web.Response
import misk.web.actions.WebAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToggleManualHealthCheckAction @Inject constructor() : WebAction {
  @Inject lateinit var manualHealthCheck: ManualHealthCheck

  @Post("/health/manual")
  fun setManualHealthCheck(@QueryParam status: String?): Response<String> {
    when (status) {
      "healthy" -> manualHealthCheck.setHealth()
      "unhealthy" -> manualHealthCheck.setUnhealthy()
      else -> return Response("The query param 'status' be one of {'healthy', 'unhealthy'}",
          statusCode = 400)
    }

    return Response(manualHealthCheck.status().toString())
  }
}
