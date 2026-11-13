package misk.audit

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.inject.typeLiteral
import misk.scope.ActionScoped
import misk.scope.ActionScopedProviderModule
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.interceptors.BeforeContentEncoding

/**
 * Opt-in HTTP completion support for [FakeAuditClientModule]. Captures wrap Misk's default request hooks, including
 * requests without audit annotations. Authentication and production hook behavior are unchanged.
 */
class FakeAuditRequestCompletionModule : ActionScopedProviderModule() {
  override fun configureProviders() {
    requireBinding<AuditClient>()
    bindProvider(AuditRequestContext::class.typeLiteral()) { AuditRequestContext() }
    multibind<NetworkInterceptor.Factory>(BeforeContentEncoding::class).to<AuditRequestCompletionInterceptor.Factory>()
  }
}

internal class AuditRequestContext {
  var registration: AuditRequestCaptures.Registration? = null
}

internal class AuditRequestCompletionInterceptor(
  private val auditClient: FakeAuditClient,
  private val requestContext: ActionScoped<AuditRequestContext>,
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val registration = auditClient.httpRequests.claim(chain.httpCall)
    requestContext.get().registration = registration
    var dispatchFailure: Throwable? = null
    try {
      chain.proceed(chain.httpCall)
    } catch (failure: Throwable) {
      dispatchFailure = failure
      throw failure
    } finally {
      if (registration != null)
        auditClient.httpRequests.finish(registration, chain.httpCall.statusCode, dispatchFailure)
    }
  }

  @Singleton
  class Factory
  @Inject
  constructor(private val auditClient: FakeAuditClient, private val requestContext: ActionScoped<AuditRequestContext>) :
    NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor =
      AuditRequestCompletionInterceptor(auditClient, requestContext)
  }
}
