package misk.audit

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.inject.typeLiteral
import misk.scope.ActionScoped
import misk.scope.ActionScopedProviderModule
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.WebActionSeedDataTransformerFactory
import misk.web.interceptors.BeforeContentEncoding

/**
 * Opt-in HTTP completion support for [FakeAuditClientModule]. Captures wrap Misk's default request hooks, including
 * requests without audit annotations. Authentication and production hook behavior are unchanged.
 */
class FakeAuditRequestCompletionModule : ActionScopedProviderModule() {
  override fun configureProviders() {
    bindProvider(AuditRequestContext::class.typeLiteral()) { AuditRequestContext() }
    multibind<WebActionSeedDataTransformerFactory>().to<AuditCaptureHeaderTransformer>()
    multibind<NetworkInterceptor.Factory>(BeforeContentEncoding::class).to<AuditRequestCompletionInterceptor.Factory>()
  }
}

internal class AuditRequestContext {
  var claimedRegistration: AuditRequestCaptures.Registration? = null
}

internal class AuditRequestCompletionInterceptor(
  private val auditClient: FakeAuditClient,
  private val requestContext: ActionScoped<AuditRequestContext>,
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    val claimedRegistration = auditClient.requestCaptures.claimAndStripHeader(chain.httpCall)
    requestContext.get().claimedRegistration = claimedRegistration
    var dispatchFailure: Throwable? = null
    try {
      chain.proceed(chain.httpCall)
    } catch (failure: Throwable) {
      dispatchFailure = failure
      throw failure
    } finally {
      if (claimedRegistration != null)
        auditClient.requestCaptures.finish(claimedRegistration, chain.httpCall.statusCode, dispatchFailure)
    }
  }

  @Singleton
  class Factory
  @Inject
  constructor(auditClient: AuditClient, private val requestContext: ActionScoped<AuditRequestContext>) :
    NetworkInterceptor.Factory {
    private val fakeAuditClient =
      checkNotNull(auditClient as? FakeAuditClient) {
        "FakeAuditRequestCompletionModule requires FakeAuditClientModule"
      }

    override fun create(action: Action): NetworkInterceptor =
      AuditRequestCompletionInterceptor(fakeAuditClient, requestContext)
  }
}
