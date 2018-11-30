package misk.security.authz

import misk.MiskCaller

/**
 * Interface for determining the current [MiskCaller]. Typically use an [ActionScoped] [Request],
 * [ActionScoped] [ClientCertSubject], etc to determine the caller based on request headers or
 * client certificate information.
 *
 * This interface is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 *
 * ```
 * multibind<MiskCallerAuthenticator>().to<MyAuthenticator>()
 * ```
 */
interface MiskCallerAuthenticator {
  fun getAuthenticatedCaller(): MiskCaller?
}