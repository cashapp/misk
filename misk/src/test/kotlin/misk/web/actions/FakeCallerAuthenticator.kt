package misk.web.actions

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.MiskCallerAuthenticator
import misk.web.Request
import javax.inject.Inject
import javax.inject.Singleton

internal const val SERVICE_HEADER = "X-Forwarded-Service"
internal const val USER_HEADER = "X-Forwarded-User"
internal const val ROLES_HEADER = "X-Forwarded-Roles"

/**
 * A caller authenticator that blindly trusts HTTP headers. Unsafe for production use.
 */
@Singleton
internal class FakeCallerAuthenticator : MiskCallerAuthenticator {
  @Inject lateinit var currentRequest: ActionScoped<Request>

  override fun getAuthenticatedCaller(): MiskCaller? {
    val request = currentRequest.get()
    val service = request.headers[SERVICE_HEADER]
    val user = request.headers[USER_HEADER]
    val roles = request.headers[ROLES_HEADER]?.split(",")?.toSet() ?: setOf()
    if (user == null && service == null) return null
    return MiskCaller(service, user, roles)
  }
}