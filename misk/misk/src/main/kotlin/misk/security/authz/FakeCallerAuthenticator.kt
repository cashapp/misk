package misk.security.authz

import com.google.inject.Inject
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.web.HttpCall
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A caller authenticator that blindly trusts HTTP headers. Unsafe for production use.
 */
@Singleton
class FakeCallerAuthenticator @Inject constructor(
  private val currentHttpCall: ActionScoped<HttpCall>
) : MiskCallerAuthenticator {
  @Inject(optional = true)
  @DevelopmentOnly
  var developmentCaller: MiskCaller? = null

  override fun getAuthenticatedCaller(): MiskCaller? {
    val httpCall = currentHttpCall.get()
    val service = httpCall.requestHeaders[SERVICE_HEADER]
    val user = httpCall.requestHeaders[USER_HEADER]
    val capabilities = httpCall.requestHeaders[CAPABILITIES_HEADER]?.split(",")?.toSet()

    val development = developmentCaller
    return when {
      !(user == null && service == null) -> MiskCaller(service = service, user = user,
          capabilities = capabilities ?: setOf())
      development != null -> MiskCaller(development.service, development.user,
          capabilities = development.capabilities)
      else -> null
    }
  }

  companion object {
    const val SERVICE_HEADER = "X-Forwarded-Service"
    const val USER_HEADER = "X-Forwarded-User"
    const val CAPABILITIES_HEADER = "X-Forwarded-Capabilities"
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DevelopmentOnly
