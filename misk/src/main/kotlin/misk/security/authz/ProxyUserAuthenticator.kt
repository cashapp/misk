package misk.security.authz

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.web.Request
import javax.inject.Inject

/**
 * Derives authentication for end users coming in through a proxy fronted by SSO
 *
 * TODO(mmihic): Potentially allow configuration of which headers contain the user
 * and which headers contain the role information
 */
class ProxyUserAuthenticator @Inject internal constructor(
  private val currentRequest: ActionScoped<Request>
) : MiskCallerAuthenticator {
  override fun getAuthenticatedCaller(): MiskCaller? {
    val request = currentRequest.get()
    val user = request.headers[HEADER_FORWARDED_USER] ?: return null
    val roles = request.headers[HEADER_FORWARDED_CAPABILITIES]
        ?.split(',')
        ?.map { it.trim() }
        ?.toSet() ?: setOf()
    return MiskCaller(user = user, roles = roles)
  }

  companion object {
    const val HEADER_FORWARDED_USER = "HTTP_X_FORWARDED_USER"
    const val HEADER_FORWARDED_CAPABILITIES = "HTTP_X_FORWARDED_CAPABILITIES"
  }
}