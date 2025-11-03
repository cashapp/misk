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
class LeaseAcquireWebAction @Inject constructor(
  private val leaseManager: LeaseManager
) : WebAction {
  @Get("/lease-acquire/{name}")
  @Unauthenticated
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  fun acquire(
    @PathParam name: String,
  ): LeaseAcquireResponse {
    // Attempt to acquire a lease (will be held for configured duration)
    val lease = leaseManager.requestLease(name)
    val acquired = lease.checkHeld()

    return LeaseAcquireResponse(
      name = name,
      acquired = acquired,
    )
  }

  data class LeaseAcquireResponse(val name: String, val acquired: Boolean)
}
