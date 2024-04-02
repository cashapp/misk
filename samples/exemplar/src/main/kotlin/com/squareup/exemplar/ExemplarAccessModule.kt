package com.squareup.exemplar

import com.cedarpolicy.BasicAuthorizationEngine
import com.cedarpolicy.model.AuthorizationRequest
import com.cedarpolicy.model.slice.BasicSlice
import com.cedarpolicy.model.slice.Entity
import com.cedarpolicy.model.slice.Policy
import com.cedarpolicy.serializer.JsonEUID
import com.cedarpolicy.value.EntityUID
import com.google.inject.Provides
import com.squareup.exemplar.dashboard.SupportDashboardAccess
import misk.Action
import misk.ApplicationInterceptor
import misk.Chain
import misk.MiskCaller
import misk.exceptions.UnauthenticatedException
import misk.exceptions.UnauthorizedException
import misk.inject.KAbstractModule
import misk.resources.ResourceLoader
import misk.scope.ActionScoped
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.AccessControlModule
import misk.security.authz.DevelopmentOnly
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.dashboard.AdminDashboardAccess
import java.util.logging.Logger
import javax.inject.Inject

class ExemplarAccessModule : KAbstractModule() {
  override fun configure() {
    install(AccessControlModule())
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()

    // Give engineers access to the admin dashboard for Exemplar service
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AdminDashboardAccess>(
        capabilities = listOf("admin_console")
      )
    )

    // Give engineers access to the admin dashboard for Exemplar service
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<SupportDashboardAccess>(
        capabilities = listOf("customer_support")
      )
    )

    // Setup authentication in the development environment
    bind<MiskCaller>().annotatedWith<DevelopmentOnly>().toInstance(
      MiskCaller(
        user = "triceratops", capabilities = setOf("admin_console", "customer_support", "users")
      )
    )

    multibind<ApplicationInterceptor.Factory>().to<PolicyInterceptor.Factory>()
  }

  @Provides
  @PolicyDocument
  fun policyDocumentReader(resourceLoader: ResourceLoader): PolicySet {
    val policies = resourceLoader.walk("classpath:/policy").fold(
      mutableSetOf<Policy>()
    ) { x, y ->
      val policyText = resourceLoader.open(y)!!.readUtf8()
      val policy = Policy(policyText, y)
      x.add(policy)
      x
    }
    return PolicySet(policies)
  }
}

data class PolicySet(
  val policies: Set<Policy>
)

// Simple policy delegate interceptor
// This requires that the consuming webaction be annotated with Unauthenticated (for PoC only)
annotation class PolicyDocument

class PolicyInterceptor(
  private val accessPolicies: Set<Policy>,
  private val caller: ActionScoped<MiskCaller?>
) :
  ApplicationInterceptor {
    private val logger = Logger.getLogger("PolicyInterceptor")
  class Factory @Inject constructor(
    @PolicyDocument val accessPolicies: PolicySet,
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>
  ) :
    ApplicationInterceptor.Factory {
    override fun create(action: Action): ApplicationInterceptor {
      return PolicyInterceptor(accessPolicies.policies, caller)
    }
  }

  override fun intercept(chain: Chain): Any {
    val caller = caller.get() ?: throw UnauthenticatedException()
    val engine = BasicAuthorizationEngine()

    val request = AuthorizationRequest(
      EntityUID("User", caller.user).toCedarExpr(),
      EntityUID("Action", "read").toCedarExpr(),
      EntityUID("Resource", "endpoint").toCedarExpr(),
      mapOf()
    )
    val slice =
      BasicSlice(accessPolicies, setOf())

    val decision = engine.isAuthorized(request, slice)
    logger.info("Decision: ${if (decision.isAllowed) "Allowed" else "Denied"}")
    logger.info("Errors ${decision.errors}")
    logger.info("Resons ${decision.reasons}")
    if (!decision.isAllowed) {
      throw UnauthorizedException("Cedar Policy denied")
    }
    return chain.proceed(chain.args)
  }
}
