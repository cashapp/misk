package misk.security.authz

import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.ssl.ClientCertSubject
import misk.security.x509.X500Name
import javax.inject.Inject

/**
 * Derives authentication information for peer services from the OU of the client provided
 * certificate.
 *
 * TODO(mmihic): Potentially allow configuration for which part of the X500 name defines
 * the service name
 */
class PeerServiceClientCertAuthenticator @Inject internal constructor(
  private @ClientCertSubject val clientCertSubject: @JvmSuppressWildcards ActionScoped<X500Name?>
) : MiskCallerAuthenticator {
  override fun getAuthenticatedCaller(): MiskCaller? {
    return clientCertSubject.get()?.organizationalUnit?.let { MiskCaller(service = it) }
  }
}