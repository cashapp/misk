package misk.security.authz

import misk.MiskCaller
import misk.scope.ActionScoped
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A caller authenticator that blindly trusts HTTP headers. Unsafe for production use.
 */
@Singleton
class FakeCallerAuthenticator @Inject constructor(
  private val currentRequest: ActionScoped<misk.web.Request>,
  private val optionalBinder: OptionalBinder
) : MiskCallerAuthenticator {

  override fun getAuthenticatedCaller(): MiskCaller? {
    val request = currentRequest.get()
    val service = request.headers[SERVICE_HEADER]
    val user = request.headers[USER_HEADER]
    val roles = request.headers[ROLES_HEADER]?.split(",")?.toSet()

    val development = optionalBinder.developmentCaller
    return when {
      !(user == null && service == null) -> MiskCaller(service = service, user = user,
          roles = roles ?: setOf())
      development != null -> MiskCaller(development.service, development.user,
          development.roles)
      else -> null
    }
  }

  companion object {
    const val SERVICE_HEADER = "X-Forwarded-Service"
    const val USER_HEADER = "X-Forwarded-User"
    const val ROLES_HEADER = "X-Forwarded-Roles"
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class DevelopmentOnly

/**
 * https://github.com/google/guice/wiki/FrequentlyAskedQuestions#how-can-i-inject-optional-parameters-into-a-constructor
 */
@Singleton
class OptionalBinder {
  @com.google.inject.Inject(optional = true)
  @DevelopmentOnly
  var developmentCaller: MiskCaller? = null
}