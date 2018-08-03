package misk.security.authz.fake

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.MiskCallerAuthenticator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A caller authenticator that blindly trusts HTTP headers. Unsafe for production use.
 */
@Singleton
class FakeCallerAuthenticator : MiskCallerAuthenticator {
  @Inject lateinit var currentRequest: ActionScoped<misk.web.Request>

  override fun getAuthenticatedCaller(): MiskCaller? {
    val request = currentRequest.get()
    val service = request.headers[SERVICE_HEADER]
    val user = request.headers[USER_HEADER]
    val roles = request.headers[ROLES_HEADER]?.split(",")?.toSet() ?: setOf()
    if (user == null && service == null) return null
    return MiskCaller(service, user, roles)
  }

  companion object {
    const val SERVICE_HEADER = "X-Forwarded-Service"
    const val USER_HEADER = "X-Forwarded-User"
    const val ROLES_HEADER = "X-Forwarded-Roles"
  }
}

