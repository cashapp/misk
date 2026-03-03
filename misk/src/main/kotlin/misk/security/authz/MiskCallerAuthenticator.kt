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
 *
 * We allow binding up many authenticators in case applications would like to support
 * alternative ways in which to authenticate requests. For example, one mechanism
 * might leverage HTTP headers while another mechanism might leverage third party
 * authentication services.
 */
interface MiskCallerAuthenticator {
  fun getAuthenticatedCaller(): MiskCaller?
}
