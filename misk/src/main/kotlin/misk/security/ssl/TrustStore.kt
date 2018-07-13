package misk.security.ssl

import java.security.KeyStore

/** A set of trusted root certificates. */
data class TrustStore(val keyStore: KeyStore)