package com.squareup.exemplar.actions

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.lease.LeaseManager

@Singleton
class LeaseCheckWebAction @Inject constructor(private val leaseManager: LeaseManager) : WebAction {
  @Get("/lease-check/{name}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  fun check(@PathParam name: String): LeaseCheckResponse {
    // By default lease will be held for 1 minute
    val lease = leaseManager.requestLease(name)
    val held = lease.checkHeld()

    return LeaseCheckResponse(name = name, held = held)
  }

  data class LeaseCheckResponse(val name: String, val held: Boolean)
}
