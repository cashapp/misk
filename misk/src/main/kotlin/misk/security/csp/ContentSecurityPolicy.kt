package misk.security.csp

/**
 * This annotation allows misk endpoints to define their Content-Security-Policy directive.
 * See https://web.archive.org/web/20220906063156/https://content-security-policy.com/
 * for a reference that's up to date when this annotation was added.
 * <p>
 * Production CSPs can be very long so a future contribution should add the ability to define
 * CSP into YAML configs and point the @Csp annotation to the config value.
 *
 * This annotation is currently dumb, it adds the rules passed to a Content-Security-Policy
 * header directive, with no inspection or validation. A future change could parse the policy.
 *
 * Developers using this should check out the various browser extensions to verify the actual
 * CSP on their webpages.
 * @param rules A list of valid Content-Security-Policy rules.
 */
annotation class ContentSecurityPolicy(val rules: Array<String>)
