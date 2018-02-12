package com.squareup.exemplar.actions

import com.squareup.exemplar.healthchecks.ManualHealthCheck
import misk.healthchecks.HealthStatus
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToggleManualHealthCheckAction : WebAction {
  @Inject lateinit var manual: ManualHealthCheck

  @Get("/_toggle_manual_healthcheck")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun toggleManual(): Response {
    val (before, after) = manual.toggle()
    return Response(
        before, after
    )
  }

  data class Response(
      val before: HealthStatus,
      val after: HealthStatus
  )
}
