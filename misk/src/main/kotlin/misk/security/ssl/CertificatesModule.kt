package misk.security.ssl

import com.google.inject.TypeLiteral
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import misk.security.cert.X500Name
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.servlet.http.HttpServletRequest

/** Installs support for accessing client certificates */
internal class CertificatesModule : ActionScopedProviderModule() {
  override fun configureProviders() {
    bindProvider(
        type = certificateArrayType,
        providerType = ClientCertProvider::class,
        annotatedBy = ClientCertChain::class.java)
    bindProvider(
        type = x500NameType,
        providerType = ClientCertSubjectDNProvider::class,
        annotatedBy = ClientCertSubject::class.java)
    bindProvider(
        type = x500NameType,
        providerType = ClientCertIssuerDNProvider::class,
        annotatedBy = ClientCertIssuer::class.java)
  }

  private class ClientCertProvider @Inject constructor() : ActionScopedProvider<Array<X509Certificate>?> {
    @Inject @JvmSuppressWildcards lateinit var request: ActionScoped<HttpServletRequest>

    override fun get(): Array<X509Certificate>? {
      @Suppress("UNCHECKED_CAST")
      return request.get().getAttribute("javax.servlet.request.X509Certificate")
          as? Array<X509Certificate>
    }
  }

  private class ClientCertSubjectDNProvider @Inject constructor() : ActionScopedProvider<X500Name?> {
    @Inject @JvmSuppressWildcards @ClientCertChain
    lateinit var clientCert: ActionScoped<Array<X509Certificate>?>

    override fun get(): X500Name? {
      return clientCert.get()?.get(0)?.subjectX500Principal?.name?.let { X500Name.parse(it) }
    }
  }

  private class ClientCertIssuerDNProvider @Inject constructor() : ActionScopedProvider<X500Name?> {
    @Inject @JvmSuppressWildcards @ClientCertChain
    lateinit var clientCert: ActionScoped<Array<X509Certificate>?>

    override fun get(): X500Name? {
      return clientCert.get()?.get(0)?.issuerX500Principal?.name?.let { X500Name.parse(it) }
    }
  }

  private companion object {
    val certificateArrayType = object : TypeLiteral<Array<X509Certificate>?>() {}
    val x500NameType = object : TypeLiteral<X500Name?>() {}
  }
}
